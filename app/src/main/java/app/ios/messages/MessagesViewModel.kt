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
import app.ios.messages.data.SimInfo
import app.ios.messages.data.Sims
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

    private val _isDefault = MutableStateFlow(repo.isDefault())
    val isDefault: StateFlow<Boolean> = _isDefault

    private val _hasPermission = MutableStateFlow(repo.canRead())
    val hasPermission: StateFlow<Boolean> = _hasPermission

    private val _sims = MutableStateFlow<List<SimInfo>>(emptyList())
    val sims: StateFlow<List<SimInfo>> = _sims

    private val _defaultSub = MutableStateFlow(prefs.defaultSub)
    val defaultSub: StateFlow<Int> = _defaultSub

    private val _setupDismissed = MutableStateFlow(prefs.setupDismissed)
    val setupDismissed: StateFlow<Boolean> = _setupDismissed

    private fun str(id: Int, vararg a: Any): String = Lang.wrap(getApplication<Application>()).getString(id, *a)

    @Volatile
    private var openThread: Long? = null
    private var job: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh(debounce = true)
        }
    }

    init {
        Notifier.ensureChannels(app)
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

    private val _notifSound = MutableStateFlow(prefs.notifSound)
    val notifSound: StateFlow<Boolean> = _notifSound
    private val _notifVibrate = MutableStateFlow(prefs.notifVibrate)
    val notifVibrate: StateFlow<Boolean> = _notifVibrate
    private val _notifHeadsUp = MutableStateFlow(prefs.notifHeadsUp)
    val notifHeadsUp: StateFlow<Boolean> = _notifHeadsUp
    private val _notifLockScreen = MutableStateFlow(prefs.notifLockScreen)
    val notifLockScreen: StateFlow<Boolean> = _notifLockScreen
    private val _notifQuickReply = MutableStateFlow(prefs.notifQuickReply)
    val notifQuickReply: StateFlow<Boolean> = _notifQuickReply
    private val _notifCopyCode = MutableStateFlow(prefs.notifCopyCode)
    val notifCopyCode: StateFlow<Boolean> = _notifCopyCode
    private val _notifGroup = MutableStateFlow(prefs.notifGroup)
    val notifGroup: StateFlow<Boolean> = _notifGroup

    fun setNotifSound(v: Boolean) { _notifSound.value = v; prefs.notifSound = v }
    fun setNotifVibrate(v: Boolean) { _notifVibrate.value = v; prefs.notifVibrate = v }
    fun setNotifHeadsUp(v: Boolean) { _notifHeadsUp.value = v; prefs.notifHeadsUp = v }
    fun setNotifLockScreen(v: Boolean) { _notifLockScreen.value = v; prefs.notifLockScreen = v }
    fun setNotifQuickReply(v: Boolean) { _notifQuickReply.value = v; prefs.notifQuickReply = v }
    fun setNotifCopyCode(v: Boolean) { _notifCopyCode.value = v; prefs.notifCopyCode = v }
    fun setNotifGroup(v: Boolean) { _notifGroup.value = v; prefs.notifGroup = v }

    fun markRead(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { repo.markRead(it) }
            val addrs = _conversations.value.filter { it.threadId in ids }.map { it.address }
            Notifier.syncRead(getApplication(), addrs)
            refresh()
        }
    }

    fun markUnread(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { repo.markUnread(it) }
            refresh()
        }
    }

    fun deleteThreads(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            _conversations.value.filter { it.threadId in ids }.forEach { prefs.setDraft(it.address, "") }
            ids.filter { it >= 0 }.forEach { repo.deleteThread(it) }
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
                _sims.value = Sims.list(getApplication())
                val base = repo.conversations().map { c ->
                    val d = prefs.draft(c.address)
                    if (d.isBlank()) c else c.copy(draft = d)
                }
                val keys = base.map { prefs.key(it.address) }.toSet()
                val extra = prefs.allDrafts()
                    .filter { prefs.key(it.first) !in keys }
                    .map { (a, t, time) ->
                        Conversation(
                            -(Math.abs(prefs.key(a).hashCode()).toLong()) - 1L, a,
                            repo.contactName(a) ?: a, "", if (time > 0L) time else System.currentTimeMillis(),
                            0, repo.contactName(a) != null, t
                        )
                    }
                val list = if (extra.isEmpty()) base else (base + extra).sortedByDescending { it.date }
                ensureActive()
                _conversations.value = list
                Notifier.syncRead(getApplication(), list.filter { it.unread == 0 }.map { it.address })
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

    fun recheckRole() {
        _isDefault.value = repo.isDefault()
        _hasPermission.value = repo.canRead()
        viewModelScope.launch {
            delay(500)
            _isDefault.value = repo.isDefault()
            delay(1500)
            _isDefault.value = repo.isDefault()
            _hasPermission.value = repo.canRead()
            refresh()
        }
    }

    fun onPermissionsChanged() {
        _hasPermission.value = repo.canRead()
        _isDefault.value = repo.isDefault()
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
            address?.let { Notifier.cancel(getApplication(), it) }
            refresh()
        }
    }

    fun closeChat() {
        openThread = null
    }

    fun clearMessages() {
        if (openThread == null) _messages.value = emptyList()
    }

    // ---- SIM selection -----------------------------------------------------------------------

    /** Order: this conversation's remembered SIM, the app default, the system's SMS SIM, first SIM. */
    fun chooseSim(address: String): Int {
        val list = _sims.value
        if (list.isEmpty()) return -1
        prefs.simFor(address)?.takeIf { s -> list.any { it.subId == s } }?.let { return it }
        _defaultSub.value.takeIf { d -> list.any { it.subId == d } }?.let { return it }
        val sys = Sims.systemDefaultSms()
        return list.firstOrNull { it.subId == sys }?.subId ?: list[0].subId
    }

    fun pickSim(address: String, subId: Int) = prefs.setSimFor(address, subId)

    fun setDefaultSub(subId: Int) {
        _defaultSub.value = subId
        prefs.defaultSub = subId
    }

    fun saveSim(subId: Int, label: String, color: Long) {
        prefs.setSim(subId, label, color)
        refresh()
    }

    fun dismissSetup() {
        _setupDismissed.value = true
        prefs.setupDismissed = true
    }

    // ---- drafts ------------------------------------------------------------------------------

    fun draftFor(address: String): String = prefs.draft(address)

    fun saveDraft(address: String, text: String) {
        prefs.setDraft(address, text)
        val d = text.takeIf { it.isNotBlank() }
        _conversations.value = _conversations.value.map {
            if (it.address == address && it.draft != d) it.copy(draft = d) else it
        }
        val key = prefs.key(address)
        if (_conversations.value.none { prefs.key(it.address) == key }) refresh(debounce = true)
        else if (d == null && _conversations.value.any { it.threadId < 0 && prefs.key(it.address) == key }) refresh()
    }

    // ---- sending -----------------------------------------------------------------------------

    fun send(address: String, body: String, attachment: String?, subId: Int, onThread: (Long) -> Unit) {
        if (subId >= 0) prefs.setSimFor(address, subId)
        prefs.setDraft(address, "")
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (attachment != null) {
                    if (!repo.isDefault()) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(getApplication(), str(R.string.toast_default_photos), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    repo.sendMms(address, body, Uri.parse(attachment), subId)
                } else {
                    repo.send(address, body, subId)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), str(R.string.not_delivered), Toast.LENGTH_SHORT).show()
                }
            }
            val tid = runCatching { repo.threadIdFor(address) }.getOrNull()
            withContext(Dispatchers.Main) { tid?.let(onThread) }
            refresh()
        }
    }

    /** Re-send a failed text, optionally through the other SIM. The failed copy is removed. */
    fun retry(address: String, failed: Message, subId: Int) {
        if (failed.images.isNotEmpty()) return
        if (subId >= 0) prefs.setSimFor(address, subId)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteMessage(failed.id)
                repo.send(address, failed.body, subId)
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(getApplication(), str(R.string.not_delivered), Toast.LENGTH_SHORT).show()
                }
            }
            refresh()
        }
    }

    private fun toast(text: String) {
        viewModelScope.launch(Dispatchers.Main) {
            Toast.makeText(getApplication(), text, Toast.LENGTH_LONG).show()
        }
    }

    fun exportBackup(uri: Uri, password: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val n = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")!!.use {
                    app.ios.messages.data.Backup.export(getApplication(), it, password.toCharArray())
                }
                toast(str(R.string.backup_done, n))
            } catch (e: Throwable) {
                toast(str(R.string.backup_failed))
            }
        }
    }

    fun restoreBackup(uri: Uri, password: String) {
        if (!repo.isDefault()) {
            toast(str(R.string.backup_need_default))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val n = getApplication<Application>().contentResolver.openInputStream(uri)!!.use {
                    app.ios.messages.data.Backup.restore(getApplication(), it, password.toCharArray())
                }
                toast(str(R.string.backup_restored, n))
                refresh()
            } catch (e: app.ios.messages.data.Backup.BadPassword) {
                toast(str(R.string.backup_wrong_password))
            } catch (e: Throwable) {
                toast(str(R.string.backup_failed))
            }
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
