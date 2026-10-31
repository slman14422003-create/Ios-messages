package app.ios.messages

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.ios.messages.data.Contact
import app.ios.messages.data.Conversation
import app.ios.messages.data.Message
import app.ios.messages.data.Prefs
import app.ios.messages.data.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MessagesViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SmsRepository(app)
    private val prefs = Prefs(app)

    private val _pinned = MutableStateFlow(prefs.pinned)
    val pinned: StateFlow<Set<Long>> = _pinned

    private val _muted = MutableStateFlow(prefs.muted)
    val muted: StateFlow<Set<Long>> = _muted

    private val _bubbleBlue = MutableStateFlow(prefs.bubbleBlue)
    val bubbleBlue: StateFlow<Boolean> = _bubbleBlue

    private val _notifications = MutableStateFlow(prefs.notifications)
    val notifications: StateFlow<Boolean> = _notifications

    private val _previews = MutableStateFlow(prefs.previews)
    val previews: StateFlow<Boolean> = _previews

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    val conversations: StateFlow<List<Conversation>> = _conversations

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private val _isDefault = MutableStateFlow(false)
    val isDefault: StateFlow<Boolean> = _isDefault

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission

    @Volatile
    private var openThread: Long? = null
    private var job: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh(debounce = true)
        }
    }

    init {
        app.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
        refresh()
        loadContacts()
    }

    fun loadContacts() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = try { repo.contacts() } catch (e: Exception) { emptyList() }
            _contacts.value = list
        }
    }

    fun togglePin(threadId: Long) {
        val s = _pinned.value.toMutableSet()
        if (!s.add(threadId)) s.remove(threadId)
        _pinned.value = s
        prefs.pinned = s
    }

    fun toggleMute(threadId: Long) {
        val s = _muted.value.toMutableSet()
        if (!s.add(threadId)) s.remove(threadId)
        _muted.value = s
        prefs.muted = s
    }

    fun setBubbleBlue(v: Boolean) {
        _bubbleBlue.value = v
        prefs.bubbleBlue = v
    }

    fun setNotifications(v: Boolean) {
        _notifications.value = v
        prefs.notifications = v
    }

    fun setPreviews(v: Boolean) {
        _previews.value = v
        prefs.previews = v
    }

    fun markRead(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { repo.markRead(it) }
            refresh()
        }
    }

    fun deleteThreads(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { repo.deleteThread(it) }
            refresh()
        }
    }

    fun refresh(debounce: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            if (debounce) delay(250)
            try {
                _hasPermission.value = repo.canRead()
                _isDefault.value = repo.isDefault()
                val list = repo.conversations()
                ensureActive()
                _conversations.value = list
                openThread?.let { tid ->
                    val m = repo.messages(tid)
                    ensureActive()
                    _messages.value = m
                    if (m.any { !it.mine && !it.read }) repo.markRead(tid)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Messages", "refresh failed", e)
            }
        }
    }

    fun onPermissionsChanged() {
        repo.clearCache()
        loadContacts()
        refresh()
    }

    fun nameFor(address: String): String = repo.contactName(address) ?: address

    fun openChat(threadId: Long?, address: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val tid = threadId ?: address?.let { runCatching { repo.threadIdFor(it) }.getOrNull() }
            if (tid != openThread) _messages.value = emptyList()
            openThread = tid
            refresh()
        }
    }

    fun closeChat() {
        openThread = null
    }

    fun clearMessages() {
        if (openThread == null) _messages.value = emptyList()
    }

    fun send(address: String, body: String, attachment: String?, onThread: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (attachment != null) {
                    if (!repo.isDefault()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), "Set Messages as default to send photos", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    repo.sendMms(address, body, Uri.parse(attachment))
                } else {
                    repo.send(address, body)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "Not Delivered", Toast.LENGTH_SHORT).show()
                }
            }
            val tid = runCatching { repo.threadIdFor(address) }.getOrNull()
            withContext(Dispatchers.Main) { tid?.let(onThread) }
            refresh()
        }
    }

    fun deleteThread(threadId: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteThread(threadId); refresh() }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { repo.deleteMessage(id); refresh() }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
