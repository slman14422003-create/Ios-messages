package app.ios.messages

import android.app.Application
import android.database.ContentObserver
import android.net.Uri
import android.provider.ContactsContract
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

    private val _trash = MutableStateFlow<List<app.ios.messages.data.TrashItem>>(emptyList())
    val trash: StateFlow<List<app.ios.messages.data.TrashItem>> = _trash

    fun loadTrash() {
        viewModelScope.launch(Dispatchers.IO) {
            _trash.value = try { app.ios.messages.data.TrashStore.list(getApplication<Application>()) } catch (e: Exception) { emptyList() }
        }
    }

    fun restoreTrash(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { app.ios.messages.data.TrashStore.restore(getApplication<Application>(), id) } catch (_: Exception) { }
            loadTrash()
            refresh()
        }
    }

    fun purgeTrash(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try { app.ios.messages.data.TrashStore.purge(getApplication<Application>(), id) } catch (_: Exception) { }
            loadTrash()
        }
    }

    private val _muted = MutableStateFlow(prefs.muted)
    val muted: StateFlow<Set<Long>> = _muted

    private val _bubbleBlue = MutableStateFlow(prefs.bubbleBlue)
    val bubbleBlue: StateFlow<Boolean> = _bubbleBlue

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts

    /** Bumped every time the address book changed and the names were reloaded: screens re-read names when it moves. */
    private val _contactsVersion = MutableStateFlow(0)
    val contactsVersion: StateFlow<Int> = _contactsVersion

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
    @Volatile
    private var simsAt = 0L
    private var job: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            refresh(debounce = true)
        }
    }

    /** Fires for any edit in any contacts app (Google, Samsung, Xiaomi...): they all write to the same provider. */
    private val contactsObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            // Contacts sync fires this constantly: only reload the address book when its fingerprint really changed.
            refresh(debounce = true, checkContacts = true)
        }
    }

    @Volatile private var contactsDirty = true
    @Volatile private var versionPending = false
    private var contactsStamp = ""

    init {
        Notifier.ensureChannels(app)
        app.contentResolver.registerContentObserver(Uri.parse("content://mms-sms/"), true, observer)
        app.contentResolver.registerContentObserver(ContactsContract.AUTHORITY_URI, true, contactsObserver)
        refresh()
        loadTrash()
        applyRetention()
    }

    /** Re-reads the address book and every displayed name on the next refresh. */
    fun loadContacts() {
        contactsDirty = true
        refresh()
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

    fun markRead(ids: List<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { repo.markRead(it) }
            val addrs = _conversations.value.filter { it.threadId in ids }.map { it.address }
            Notifier.syncRead(getApplication(), addrs)
            refresh()
        }
    }

    fun applyRetention() {
        viewModelScope.launch(Dispatchers.IO) {
            val d = Prefs(getApplication<Application>()).keepDays
            if (d > 0) {
                repo.deleteOlderThan(d)
                refresh()
            }
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
            if (repo.isDefault()) {
                ids.filter { it >= 0 }.forEach { id ->
                    val nm = _conversations.value.firstOrNull { it.threadId == id }?.name.orEmpty()
                    app.ios.messages.data.TrashStore.put(getApplication<Application>(), id, nm)
                }
            }
            ids.filter { it >= 0 }.forEach { repo.deleteThread(it) }
            loadTrash()
            refresh()
        }
    }

    fun refresh(debounce: Boolean = false, checkContacts: Boolean = false) {
        job?.cancel()
        job = viewModelScope.launch(Dispatchers.IO) {
            if (debounce) delay(300)
            try {
                // The address book may have changed while the app was away (or the change event was missed).
                if (checkContacts) {
                    val st = repo.contactsStamp()
                    if (st != contactsStamp) {
                        contactsStamp = st
                        contactsDirty = true
                    }
                }
                if (contactsDirty) {
                    contactsDirty = false
                    versionPending = true
                    repo.clearCache()
                    _contacts.value = try { repo.contacts() } catch (e: Exception) { emptyList() }
                }
                _hasPermission.value = repo.canRead()
                _isDefault.value = repo.isDefault()
                val now = System.currentTimeMillis()
                if (now - simsAt > 10_000L || _sims.value.isEmpty()) {
                    _sims.value = Sims.list(getApplication())
                    simsAt = now
                }
                // One pass over the stored drafts (each one is decrypted), not one lookup per conversation.
                val drafts = prefs.allDrafts()
                val draftByKey = HashMap<String, String>(drafts.size)
                drafts.forEach { (a, t, _) -> draftByKey[prefs.key(a)] = t }
                val base = repo.conversations().map { c ->
                    val d = draftByKey[prefs.key(c.address)]
                    if (d.isNullOrBlank()) c else c.copy(draft = d)
                }
                val keys = base.map { prefs.key(it.address) }.toSet()
                val extra = drafts
                    .filter { prefs.key(it.first) !in keys }
                    .map { (a, t, time) ->
                        Conversation(
                            -(Math.abs(prefs.key(a).hashCode()).toLong()) - 1L, a,
                            repo.contactName(a) ?: a, "", if (time > 0L) time else System.currentTimeMillis(),
                            0, repo.contactName(a) != null, t, photo = repo.contactPhoto(a)
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
                if (versionPending) {
                    versionPending = false
                    _contactsVersion.value = _contactsVersion.value + 1
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Messages", "refresh failed", e)
            }
        }
    }

    fun recheckRole() {
        val d0 = repo.isDefault()
        val p0 = repo.canRead()
        _isDefault.value = d0
        _hasPermission.value = p0
        viewModelScope.launch {
            delay(500)
            _isDefault.value = repo.isDefault()
            delay(1500)
            val d1 = repo.isDefault()
            val p1 = repo.canRead()
            _isDefault.value = d1
            _hasPermission.value = p1
            // Only reload everything when the role / permission really changed (it used to reload on every resume).
            if (d1 != d0 || p1 != p0) refresh()
        }
    }

    fun onPermissionsChanged() {
        _hasPermission.value = repo.canRead()
        _isDefault.value = repo.isDefault()
        loadContacts()
    }

    /** Cache hit in the normal case; a miss is one indexed lookup (the cache lock is no longer held during queries). */
    fun nameFor(address: String): String = repo.cachedName(address) ?: repo.quickName(address) ?: address

    /** Contact photo thumbnail URI for an address, or null. Cache only: safe while composing. */
    fun photoFor(address: String): String? = repo.cachedPhoto(address)

    fun openChat(threadId: Long?, address: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val tid = threadId ?: address?.let { runCatching { repo.threadIdFor(it) }.getOrNull() }
            if (tid != openThread) _messages.value = emptyList()
            openThread = tid
            AppState.openKey = address?.let { prefs.key(it) }
            address?.let { Notifier.cancel(getApplication(), it) }
            // Load just this conversation first so the screen can slide in right away; the full
            // list refresh (several provider queries) happens after.
            if (tid != null) {
                try {
                    val m = repo.messages(tid)
                    if (openThread == tid) _messages.value = m
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            // The full refresh (many provider queries + big recomposition) is started by the UI once the
            // slide-in animation has finished (see App in MainActivity).
        }
    }

    fun closeChat() {
        openThread = null
        AppState.openKey = null
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
        simsAt = 0L
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
        Sounds.playSent(getApplication())
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
        val cr = getApplication<Application>().contentResolver
        cr.unregisterContentObserver(observer)
        cr.unregisterContentObserver(contactsObserver)
    }
}
