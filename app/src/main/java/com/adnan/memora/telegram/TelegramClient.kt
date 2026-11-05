package com.adnan.memora.telegram

import android.content.Context
import android.os.Build
import android.util.Base64
import com.adnan.memora.BuildConfig
import com.adnan.memora.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Locale
import java.util.UUID

sealed class TgAuth {
    object NeedCredentials : TgAuth()
    object Starting : TgAuth()
    object NeedPhone : TgAuth()
    object NeedCode : TgAuth()
    class NeedPassword(val hint: String) : TgAuth()
    object Ready : TgAuth()
    class Failed(val message: String) : TgAuth()
}

sealed class StorageStatus {
    object Unknown : StorageStatus()
    object Checking : StorageStatus()
    object Verified : StorageStatus()
    class Problem(val message: String) : StorageStatus()
}

sealed class UploadResult {
    class Success(val messageId: Long) : UploadResult()
    class Failure(val message: String) : UploadResult()
    class Uncertain(val messageId: Long = 0L) : UploadResult()
}

/** A file found in the storage channel while rebuilding the cloud library. */
class CloudDoc(
    val chatId: Long, val messageId: Long, val name: String, val mime: String, val size: Long,
    val sha: String?, val takenMs: Long, val dateMs: Long,
)

class StorageCandidate(val chatId: Long, val storageId: String, val title: String)

class ThumbInfo(val path: String, val width: Int, val height: Int)

class TgUser(val id: Long, val name: String, val phone: String)

/**
 * Talks to Telegram through TDLib (official login: phone, code, optional two-step password).
 * Memora never sees or stores the Telegram password or the login code: they go straight to TDLib.
 */
class TelegramClient private constructor(private val app: Context) {
    companion object {
        @Volatile private var instance: TelegramClient? = null
        fun get(ctx: Context): TelegramClient =
            instance ?: synchronized(this) { instance ?: TelegramClient(ctx.applicationContext).also { instance = it } }
    }

    private val _auth = MutableStateFlow<TgAuth>(if (TelegramConfig.credentials(app) != null) TgAuth.Starting else TgAuth.NeedCredentials)
    val auth: StateFlow<TgAuth> = _auth
    private val _me = MutableStateFlow<TgUser?>(null)
    val me: StateFlow<TgUser?> = _me
    private val _storage = MutableStateFlow(StorageStore.load(app))
    val storage: StateFlow<StorageConfig?> = _storage
    private val _status = MutableStateFlow<StorageStatus>(StorageStatus.Unknown)
    val storageStatus: StateFlow<StorageStatus> = _status
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var client: Client? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun clearError() { _error.value = null }

    /** Starts TDLib. Safe to call many times. */
    @Synchronized
    fun start() {
        if (client != null) return
        if (TelegramConfig.credentials(app) == null) { _auth.value = TgAuth.NeedCredentials; return }
        try {
            try { System.loadLibrary("tdjni") } catch (_: Throwable) {}
            try { Client.execute(TdApi.SetLogVerbosityLevel(1)) } catch (_: Throwable) {}
            _auth.value = TgAuth.Starting
            client = Client.create({ obj -> onUpdate(obj) }, null, null)
        } catch (t: Throwable) {
            client = null
            _auth.value = TgAuth.Failed(app.getString(R.string.tg_lib_unavailable))
        }
    }

    private fun send(query: TdApi.Function<*>, cb: (TdApi.Object) -> Unit) {
        val c = client ?: return
        c.send(query) { r -> cb(r) }
    }

    private val sendResults = ConcurrentHashMap<Long, CompletableDeferred<UploadResult>>()
    private val progressByFile = ConcurrentHashMap<Int, (Float) -> Unit>()
    private val downloadProgress = ConcurrentHashMap<Int, (Float) -> Unit>()
    private val thumbPaths = ConcurrentHashMap<String, String>()
    private fun deferredFor(tempId: Long): CompletableDeferred<UploadResult> =
        sendResults.getOrPut(tempId) { CompletableDeferred() }

    private fun onUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> handleAuth(obj.authorizationState)
            is TdApi.UpdateMessageSendSucceeded -> deferredFor(obj.oldMessageId).complete(UploadResult.Success(obj.message.id))
            is TdApi.UpdateMessageSendFailed -> deferredFor(obj.oldMessageId).complete(UploadResult.Failure(app.getString(R.string.bk_err_send_failed)))
            is TdApi.UpdateFile -> {
                val f = obj.file
                val cb = progressByFile[f.id]
                if (cb != null && f.size > 0) cb((f.remote.uploadedSize.toFloat() / f.size).coerceIn(0f, 1f))
                val dcb = downloadProgress[f.id]
                val total = if (f.size > 0) f.size else f.expectedSize
                if (dcb != null && total > 0) dcb((f.local.downloadedSize.toFloat() / total).coerceIn(0f, 1f))
            }
            else -> {}
        }
    }

    private fun handleAuth(state: TdApi.AuthorizationState) {
        _busy.value = false
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParameters()
            is TdApi.AuthorizationStateWaitPhoneNumber -> _auth.value = TgAuth.NeedPhone
            is TdApi.AuthorizationStateWaitCode -> _auth.value = TgAuth.NeedCode
            is TdApi.AuthorizationStateWaitPassword -> _auth.value = TgAuth.NeedPassword(state.passwordHint ?: "")
            is TdApi.AuthorizationStateReady -> {
                _auth.value = TgAuth.Ready
                releaseFullFiles() // remove temporary full-size files left from a previous session
                loadMe()
            }
            is TdApi.AuthorizationStateLoggingOut -> _auth.value = TgAuth.Starting
            is TdApi.AuthorizationStateClosing -> {}
            is TdApi.AuthorizationStateClosed -> {
                client = null
                _me.value = null
                _status.value = StorageStatus.Unknown
                start() // new client so the user can sign in again after logging out
            }
            else -> _auth.value = TgAuth.Failed(app.getString(R.string.tg_unsupported_step))
        }
    }

    private fun sendParameters() {
        val creds = TelegramConfig.credentials(app)
        if (creds == null) { _auth.value = TgAuth.NeedCredentials; return }
        val p = TdApi.SetTdlibParameters()
        p.useTestDc = false
        p.databaseDirectory = File(app.filesDir, "tdlib").absolutePath
        p.filesDirectory = File(app.filesDir, "tdlib_files").absolutePath
        p.databaseEncryptionKey = SecureKeys.databaseKey(app)
        p.useFileDatabase = true
        p.useChatInfoDatabase = true
        p.useMessageDatabase = true
        p.useSecretChats = false
        p.apiId = creds.first
        p.apiHash = creds.second
        p.systemLanguageCode = Locale.getDefault().language
        p.deviceModel = Build.MODEL
        p.systemVersion = Build.VERSION.RELEASE
        p.applicationVersion = BuildConfig.VERSION_NAME
        send(p) { r -> if (r is TdApi.Error) fail(r) }
    }

    private fun fail(e: TdApi.Error) {
        _busy.value = false
        _error.value = friendly(e.message ?: "")
    }

    private fun friendly(m: String): String = when {
        m.contains("PHONE_NUMBER_INVALID") -> app.getString(R.string.tg_error_phone)
        m.contains("PHONE_CODE") -> app.getString(R.string.tg_error_code)
        m.contains("PASSWORD_HASH_INVALID") -> app.getString(R.string.tg_error_password)
        m.contains("FLOOD") -> app.getString(R.string.tg_error_flood)
        else -> app.getString(R.string.tg_error_generic, m)
    }

    /** Saves the user's own API id/hash (encrypted) and starts Telegram. Returns false if the values look wrong. */
    fun saveCredentials(idText: String, hash: String): Boolean {
        val id = idText.trim().toIntOrNull()
        val h = hash.trim().lowercase(Locale.ROOT)
        if (id == null || id <= 0 || h.length != 32 || !h.all { it in '0'..'9' || it in 'a'..'f' }) {
            _error.value = app.getString(R.string.tg_cred_invalid)
            return false
        }
        _error.value = null
        SecureKeys.saveApiCredentials(app, id, h)
        start()
        return true
    }

    /** Lets the user enter a different API key (only while signed out). */
    fun changeCredentials() {
        SecureKeys.clearApiCredentials(app)
        _error.value = null
        val c = client
        if (c == null) {
            start()
        } else {
            send(TdApi.Close()) { } // when TDLib reports Closed, start() runs again and asks for the key
        }
    }

    fun submitPhone(phone: String) {
        _error.value = null
        _busy.value = true
        val q = TdApi.SetAuthenticationPhoneNumber()
        q.phoneNumber = phone.trim()
        send(q) { r -> if (r is TdApi.Error) fail(r) }
    }

    fun submitCode(code: String) {
        _error.value = null
        _busy.value = true
        val q = TdApi.CheckAuthenticationCode()
        q.code = code.trim()
        send(q) { r -> if (r is TdApi.Error) fail(r) }
    }

    fun submitPassword(password: String) {
        _error.value = null
        _busy.value = true
        val q = TdApi.CheckAuthenticationPassword()
        q.password = password
        send(q) { r -> if (r is TdApi.Error) fail(r) }
    }

    fun logOut() {
        _error.value = null
        send(TdApi.LogOut()) { r -> if (r is TdApi.Error) fail(r) }
    }

    private fun loadMe() {
        send(TdApi.GetMe()) { r ->
            if (r is TdApi.User) {
                val name = listOf(r.firstName ?: "", r.lastName ?: "").filter { it.isNotBlank() }.joinToString(" ")
                _me.value = TgUser(r.id, name, r.phoneNumber ?: "")
                if (_storage.value != null) verifyStorage()
            } else if (r is TdApi.Error) fail(r)
        }
    }

    /** Creates the private "Memora Cloud Backup" channel. Only called after the user confirms. */
    fun createStorage() {
        if (_busy.value || _me.value == null) return
        _busy.value = true
        _error.value = null
        val storageId = UUID.randomUUID().toString()
        val q = TdApi.CreateNewSupergroupChat()
        q.title = "Memora Cloud Backup"
        q.isChannel = true
        q.description = "MEMORA_CLOUD v1 id=$storageId. Private backup storage made by Memora Cloud. Please do not delete this channel."
        send(q) { r ->
            if (r is TdApi.Chat) {
                val cfg = StorageConfig(r.id, storageId, r.title ?: "Memora Cloud Backup", System.currentTimeMillis())
                StorageStore.save(app, cfg)
                _storage.value = cfg
                _busy.value = false
                verifyStorage()
                scope.launch { writeSavedMarker(r.id, storageId) }
            } else if (r is TdApi.Error) fail(r)
        }
    }

    /** Checks the channel really exists, is a channel, belongs to this account and carries our Memora ID. */
    fun verifyStorage() {
        val cfg = _storage.value ?: return
        val uid = _me.value?.id ?: return
        _status.value = StorageStatus.Checking
        val getChat = TdApi.GetChat()
        getChat.chatId = cfg.chatId
        send(getChat) { chat ->
            if (chat !is TdApi.Chat) { problem(chat); return@send }
            val type = chat.type
            if (type !is TdApi.ChatTypeSupergroup || !type.isChannel) {
                _status.value = StorageStatus.Problem(app.getString(R.string.tg_problem_not_channel)); return@send
            }
            val sender = TdApi.MessageSenderUser()
            sender.userId = uid
            val getMember = TdApi.GetChatMember()
            getMember.chatId = cfg.chatId
            getMember.memberId = sender
            send(getMember) { member ->
                if (member !is TdApi.ChatMember) { problem(member); return@send }
                val st = member.status
                val owner = st is TdApi.ChatMemberStatusCreator
                val admin = st is TdApi.ChatMemberStatusAdministrator
                if (!owner && !admin) {
                    _status.value = StorageStatus.Problem(app.getString(R.string.tg_problem_no_access)); return@send
                }
                val getFull = TdApi.GetSupergroupFullInfo()
                getFull.supergroupId = type.supergroupId
                send(getFull) { full ->
                    if (full !is TdApi.SupergroupFullInfo) { problem(full); return@send }
                    if ((full.description ?: "").contains(cfg.storageId)) _status.value = StorageStatus.Verified
                    else _status.value = StorageStatus.Problem(app.getString(R.string.tg_problem_id_mismatch))
                }
            }
        }
    }

    private fun problem(o: TdApi.Object) {
        val m = if (o is TdApi.Error) (o.message ?: "") else ""
        _status.value = StorageStatus.Problem(app.getString(R.string.tg_problem_unreachable, m))
    }

    private suspend fun sendAndWait(q: TdApi.Function<*>, timeoutMs: Long? = null): TdApi.Object? {
        val c = client ?: return null
        val request: suspend () -> TdApi.Object? = {
            suspendCancellableCoroutine { cont -> c.send(q) { r -> if (cont.isActive) cont.resume(r) } }
        }
        return if (timeoutMs == null) request() else withTimeoutOrNull(timeoutMs) { request() }
    }

    /** Explicit, non-destructive convenience action. Does NOT prevent Telegram-side deletion. */
    suspend fun archiveMuteBackupChannel(): Pair<Boolean, Boolean> {
        val target = storage.value ?: return false to false
        fun sameDestination() = auth.value is TgAuth.Ready && storageStatus.value is StorageStatus.Verified &&
            storage.value?.chatId == target.chatId && storage.value?.storageId == target.storageId
        if (!sameDestination()) return false to false
        val get = TdApi.GetChat().apply { chatId = target.chatId }
        val chat = sendAndWait(get, 30_000L) as? TdApi.Chat ?: return false to false
        if (!sameDestination()) return false to false
        // Preserve every other notification choice from the current remote settings.
        val notification = chat.notificationSettings ?: return false to false
        notification.useDefaultMuteFor = false
        notification.muteFor = Int.MAX_VALUE
        val mute = TdApi.SetChatNotificationSettings().apply {
            chatId = target.chatId; notificationSettings = notification
        }
        val muted = sendAndWait(mute, 30_000L) is TdApi.Ok
        if (!sameDestination()) return muted to false
        val archive = TdApi.AddChatToList().apply {
            chatId = target.chatId; chatList = TdApi.ChatListArchive()
        }
        val archived = sendAndWait(archive, 30_000L) is TdApi.Ok
        return muted to archived
    }

    /** Waits until Telegram is signed in and the storage channel is verified. */
    suspend fun awaitUsable(timeoutMs: Long = 60_000): Boolean {
        start()
        val ready = withTimeoutOrNull(timeoutMs) { auth.first { it is TgAuth.Ready } } != null
        if (!ready || storage.value == null) return false
        if (_status.value is StorageStatus.Unknown) verifyStorage()
        val r = withTimeoutOrNull(30_000L) { storageStatus.first { it is StorageStatus.Verified || it is StorageStatus.Problem } }
        return r is StorageStatus.Verified
    }

    /** Uploads a local file as a DOCUMENT (original quality, no compression). Returns when Telegram confirms. */
    suspend fun uploadDocument(chatId: Long, path: String, caption: String, thumb: ThumbInfo?, onProgress: (Float) -> Unit): UploadResult {
        val file = TdApi.InputFileLocal()
        file.path = path
        val text = TdApi.FormattedText()
        text.text = caption
        text.entities = emptyArray()
        // Current TDLib wraps the file in an InputDocument that carries the "send as file" flag.
        val inputDocument = TdApi.InputDocument()
        inputDocument.document = file
        inputDocument.disableContentTypeDetection = true
        if (thumb != null) {
            val tf = TdApi.InputFileLocal()
            tf.path = thumb.path
            val it = TdApi.InputThumbnail()
            it.thumbnail = tf
            it.width = thumb.width
            it.height = thumb.height
            inputDocument.thumbnail = it
        }
        val content = TdApi.InputMessageDocument()
        content.document = inputDocument
        content.caption = text
        val req = TdApi.SendMessage()
        req.chatId = chatId
        req.inputMessageContent = content

        val sent = sendAndWait(req, 60_000L) ?: return UploadResult.Uncertain()
        if (sent is TdApi.Error) return UploadResult.Failure(friendly(sent.message ?: ""))
        val msg = sent as? TdApi.Message ?: return UploadResult.Failure(app.getString(R.string.bk_err_send_failed))
        if (msg.sendingState == null) return UploadResult.Success(msg.id)
        val temp = msg.id
        val fileId = (msg.content as? TdApi.MessageDocument)?.document?.document?.id
        val clock = { System.nanoTime() / 1_000_000 }
        val lastProgress = java.util.concurrent.atomic.AtomicLong(clock())
        var highest = -1f
        if (fileId != null) progressByFile[fileId] = { progress ->
            if (progress > highest) { highest = progress; lastProgress.set(clock()) }
            onProgress(progress)
        }
        val deferred = deferredFor(temp)
        try {
            return UploadAwaiter.await(deferred, { lastProgress.get() }, clock) ?: UploadResult.Uncertain(temp)
        } catch (e: CancellationException) {
            // upload was interrupted: cancel the pending message so it is not sent twice later
            val del = TdApi.DeleteMessages()
            del.chatId = chatId
            del.messageIds = longArrayOf(temp)
            del.revoke = true
            client?.send(del) { }
            throw e
        } finally {
            sendResults.remove(temp)
            if (fileId != null) progressByFile.remove(fileId)
        }
    }

    /** Asks Telegram for the message back and checks the stored file has the expected size. */
    suspend fun verifyMessage(chatId: Long, messageId: Long, expectedSize: Long): Boolean {
        val q = TdApi.GetMessage()
        q.chatId = chatId
        q.messageId = messageId
        val m = sendAndWait(q, 60_000L) as? TdApi.Message ?: return false
        val f = (m.content as? TdApi.MessageDocument)?.document?.document ?: return false
        return f.size == expectedSize || f.expectedSize == expectedSize
    }

    // ---------- cloud viewing: temporary, app-private, never in the user's gallery ----------

    private suspend fun getMessage(chatId: Long, messageId: Long): TdApi.Message? {
        val q = TdApi.GetMessage()
        q.chatId = chatId
        q.messageId = messageId
        return sendAndWait(q) as? TdApi.Message
    }

    private suspend fun downloadSync(fileId: Int, onProgress: ((Float) -> Unit)? = null): String? {
        if (onProgress != null) downloadProgress[fileId] = onProgress
        val q = TdApi.DownloadFile()
        q.fileId = fileId
        q.priority = 16
        q.offset = 0
        q.limit = 0
        q.synchronous = true
        val r = sendAndWait(q)
        downloadProgress.remove(fileId)
        val f = r as? TdApi.File ?: return null
        return if (f.local.isDownloadingCompleted && !f.local.path.isNullOrEmpty()) f.local.path else null
    }

    /** Small preview for the Cloud grid. Uses the preview uploaded with the file; older uploads fall back to the photo itself (small files only). */
    suspend fun cloudThumbPath(chatId: Long, messageId: Long): String? {
        val key = "$chatId:$messageId"
        thumbPaths[key]?.let { if (File(it).exists()) return it }
        val doc = (getMessage(chatId, messageId)?.content as? TdApi.MessageDocument)?.document ?: return null
        val target = doc.thumbnail?.file
            ?: if ((doc.mimeType ?: "").startsWith("image/") && doc.document.size in 1..15_000_000L) doc.document else null
        if (target == null) return null
        val p = downloadSync(target.id) ?: return null
        thumbPaths[key] = p
        return p
    }

    private fun cachePrefs() = app.getSharedPreferences("memora_cloud_cache", Context.MODE_PRIVATE)

    /** Downloads the original into a TEMPORARY app-private cache (for viewing/saving). It is deleted by [releaseFullFiles]. */
    suspend fun cloudFullPath(chatId: Long, messageId: Long, onProgress: ((Float) -> Unit)? = null): String? {
        val doc = (getMessage(chatId, messageId)?.content as? TdApi.MessageDocument)?.document ?: return null
        val id = doc.document.id
        val ids = cachePrefs().getStringSet("ids", emptySet())!!.toMutableSet()
        ids.add(id.toString())
        cachePrefs().edit().putStringSet("ids", ids).apply()
        return downloadSync(id, onProgress)
    }

    /** Deletes the temporary full-size files so cloud originals never stay on the phone. */
    fun releaseFullFiles() {
        val ids = cachePrefs().getStringSet("ids", emptySet()) ?: emptySet()
        if (ids.isEmpty()) return
        for (s in ids) {
            val id = s.toIntOrNull() ?: continue
            val d = TdApi.DeleteFile()
            d.fileId = id
            client?.send(d) { }
        }
        cachePrefs().edit().remove("ids").apply()
    }

    suspend fun deleteMessages(chatId: Long, ids: List<Long>): Boolean {
        if (ids.isEmpty()) return true
        val q = TdApi.DeleteMessages()
        q.chatId = chatId
        q.messageIds = ids.toLongArray()
        q.revoke = true
        return sendAndWait(q) is TdApi.Ok
    }

    // ---------- recovery: find the storage again and read the library back ----------

    suspend fun awaitSignedIn(timeoutMs: Long = 60_000): Boolean {
        start()
        val ready = withTimeoutOrNull(timeoutMs) { auth.first { it is TgAuth.Ready } } != null
        if (!ready) return false
        return withTimeoutOrNull(30_000L) { me.first { it != null } } != null
    }

    private suspend fun savedChatId(): Long? {
        val uid = _me.value?.id ?: return null
        val q = TdApi.CreatePrivateChat()
        q.userId = uid
        q.force = true
        return (sendAndWait(q) as? TdApi.Chat)?.id
    }

    /** A small note in the user's own Saved Messages that points to the storage channel (helps recovery find it). */
    suspend fun writeSavedMarker(chatId: Long, storageId: String) {
        val saved = savedChatId() ?: return
        val ft = TdApi.FormattedText()
        ft.text = "MEMORA_CLOUD_MARKER v1 id=$storageId chat=$chatId"
        ft.entities = emptyArray()
        val content = TdApi.InputMessageText()
        content.text = ft
        val m = TdApi.SendMessage()
        m.chatId = saved
        m.inputMessageContent = content
        sendAndWait(m)
    }

    suspend fun savedChat(): Long? = savedChatId()

    private suspend fun historyPage(chatId: Long, from: Long): Array<TdApi.Message> {
        val q = TdApi.GetChatHistory()
        q.chatId = chatId
        q.fromMessageId = from
        q.offset = 0
        q.limit = 100
        q.onlyLocal = false
        return (sendAndWait(q) as? TdApi.Messages)?.messages ?: emptyArray()
    }

    /** Writes the key of the encrypted metadata file into the user's own Saved Messages (once per storage). */
    suspend fun ensureKeyNote(storageId: String, key: ByteArray) {
        val flag = "key_note_$storageId"
        if (cachePrefs().getBoolean(flag, false)) return
        val saved = savedChatId() ?: return
        val ft = TdApi.FormattedText()
        ft.text = "MEMORA_CLOUD_KEY v1 id=$storageId key=" + Base64.encodeToString(key, Base64.NO_WRAP)
        ft.entities = emptyArray()
        val content = TdApi.InputMessageText()
        content.text = ft
        val m = TdApi.SendMessage()
        m.chatId = saved
        m.inputMessageContent = content
        if (sendAndWait(m) is TdApi.Message) cachePrefs().edit().putBoolean(flag, true).apply()
    }

    suspend fun findKeyNote(storageId: String): ByteArray? {
        val saved = savedChatId() ?: return null
        val rx = Regex("MEMORA_CLOUD_KEY v1 id=" + Regex.escape(storageId) + " key=([A-Za-z0-9+/=]+)")
        var from = 0L
        for (page in 0 until 15) {
            val msgs = historyPage(saved, from)
            if (msgs.isEmpty()) break
            for (m in msgs) {
                val t = (m.content as? TdApi.MessageText)?.text?.text ?: continue
                val g = rx.find(t)?.groupValues?.get(1) ?: continue
                return try { Base64.decode(g, Base64.NO_WRAP) } catch (_: Exception) { null }
            }
            val next = msgs.minOf { it.id }
            if (next == from) break
            from = next
        }
        return null
    }

    /** File id of the newest encrypted metadata file in the storage channel. */
    suspend fun findLatestMeta(chatId: Long): Int? {
        var from = 0L
        for (page in 0 until 40) {
            val msgs = historyPage(chatId, from)
            if (msgs.isEmpty()) break
            for (m in msgs.sortedByDescending { it.id }) {
                val c = m.content as? TdApi.MessageDocument ?: continue
                if (c.caption?.text?.startsWith("memora:meta:v1") == true) return c.document.document.id
            }
            val next = msgs.minOf { it.id }
            if (next == from) break
            from = next
        }
        return null
    }

    suspend fun downloadFile(fileId: Int): String? = downloadSync(fileId)

    fun deleteFile(fileId: Int) {
        val d = TdApi.DeleteFile()
        d.fileId = fileId
        client?.send(d) { }
    }

    /** Looks for the user's Memora storage channel(s): Saved Messages notes first, then a search by name. */
    suspend fun findStorageCandidates(): List<StorageCandidate> {
        val uid = _me.value?.id ?: return emptyList()
        val ids = LinkedHashSet<Long>()

        val saved = savedChatId()
        if (saved != null) {
            var from = 0L
            for (page in 0 until 10) {
                val q = TdApi.GetChatHistory()
                q.chatId = saved
                q.fromMessageId = from
                q.offset = 0
                q.limit = 100
                q.onlyLocal = false
                val msgs = (sendAndWait(q) as? TdApi.Messages)?.messages ?: emptyArray()
                if (msgs.isEmpty()) break
                for (m in msgs) {
                    val t = (m.content as? TdApi.MessageText)?.text?.text ?: continue
                    Regex("MEMORA_CLOUD_MARKER v1 id=\\S+ chat=(-?\\d+)").find(t)?.groupValues?.get(1)?.toLongOrNull()?.let { ids.add(it) }
                }
                val next = msgs.minOf { it.id }
                if (next == from) break
                from = next
            }
        }
        val local = TdApi.SearchChats()
        local.query = "Memora"
        local.limit = 50
        (sendAndWait(local) as? TdApi.Chats)?.chatIds?.forEach { ids.add(it) }
        val server = TdApi.SearchChatsOnServer()
        server.query = "Memora"
        server.limit = 50
        (sendAndWait(server) as? TdApi.Chats)?.chatIds?.forEach { ids.add(it) }

        val out = ArrayList<StorageCandidate>()
        for (id in ids) {
            val getChat = TdApi.GetChat()
            getChat.chatId = id
            val chat = sendAndWait(getChat) as? TdApi.Chat ?: continue
            val type = chat.type as? TdApi.ChatTypeSupergroup ?: continue
            if (!type.isChannel) continue
            val sender = TdApi.MessageSenderUser()
            sender.userId = uid
            val getMember = TdApi.GetChatMember()
            getMember.chatId = id
            getMember.memberId = sender
            val member = sendAndWait(getMember) as? TdApi.ChatMember ?: continue
            val st = member.status
            if (st !is TdApi.ChatMemberStatusCreator && st !is TdApi.ChatMemberStatusAdministrator) continue
            val getFull = TdApi.GetSupergroupFullInfo()
            getFull.supergroupId = type.supergroupId
            val full = sendAndWait(getFull) as? TdApi.SupergroupFullInfo ?: continue
            val m = Regex("MEMORA_CLOUD v1 id=([0-9a-fA-F-]{36})").find(full.description ?: "") ?: continue
            if (out.none { it.chatId == id }) out.add(StorageCandidate(id, m.groupValues[1], chat.title ?: ""))
        }
        return out
    }

    /** Use an existing storage channel (found during recovery) as this phone's storage. */
    fun adoptStorage(c: StorageCandidate) {
        val cfg = StorageConfig(c.chatId, c.storageId, c.title, System.currentTimeMillis())
        StorageStore.save(app, cfg)
        _storage.value = cfg
        verifyStorage()
    }

    private fun parseDoc(chatId: Long, m: TdApi.Message): CloudDoc? {
        val c = m.content as? TdApi.MessageDocument ?: return null
        val cap = c.caption?.text ?: ""
        if (!cap.startsWith("memora:v1")) return null
        val sha = Regex("sha256=([0-9a-fA-F]{64})").find(cap)?.groupValues?.get(1)?.lowercase()
        val taken = Regex("taken=(\\d+)").find(cap)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val d = c.document
        val dateMs = m.date * 1000L
        return CloudDoc(
            chatId, m.id, d.fileName ?: "file", d.mimeType ?: "application/octet-stream", d.document.size,
            sha, if (taken > 0) taken else dateMs, dateMs,
        )
    }

    /** Reads the whole channel history and returns every Memora file in it (newest first). */
    suspend fun scanHistory(chatId: Long, onProgress: (Int) -> Unit): List<CloudDoc> {
        val out = LinkedHashMap<Long, CloudDoc>()
        val seen = HashSet<Long>()
        var from = 0L
        var stagnant = 0
        while (true) {
            val q = TdApi.GetChatHistory()
            q.chatId = chatId
            q.fromMessageId = from
            q.offset = 0
            q.limit = 100
            q.onlyLocal = false
            val r = sendAndWait(q) as? TdApi.Messages ?: break
            val msgs = r.messages ?: emptyArray()
            if (msgs.isEmpty()) break
            var added = 0
            for (m in msgs) {
                if (!seen.add(m.id)) continue
                added++
                parseDoc(chatId, m)?.let { out[m.id] = it }
            }
            onProgress(out.size)
            if (added == 0) {
                stagnant++
                if (stagnant >= 5) break
                delay(400)
            } else {
                stagnant = 0
                from = msgs.minOf { it.id }
            }
        }
        return out.values.toList()
    }

    /** Removes the storage from this app only. The Telegram channel and its content are untouched. */
    fun forgetStorage() {
        StorageStore.clear(app)
        _storage.value = null
        _status.value = StorageStatus.Unknown
    }
}
