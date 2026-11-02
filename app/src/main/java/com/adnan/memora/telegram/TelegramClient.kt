package com.adnan.memora.telegram

import android.content.Context
import android.os.Build
import com.adnan.memora.BuildConfig
import com.adnan.memora.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

class TgUser(val id: Long, val name: String, val phone: String)

/** An upload that did not finish. permanent = retrying the same file cannot help. */
class TgUploadException(message: String, val permanent: Boolean) : Exception(message)

private class PendingUpload(val onProgress: (Float) -> Unit, val cont: CancellableContinuation<TdApi.Message>)

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

    private fun onUpdate(obj: TdApi.Object) {
        when (obj) {
            is TdApi.UpdateAuthorizationState -> handleAuth(obj.authorizationState)
            is TdApi.UpdateFile -> onFileUpdate(obj.file)
            is TdApi.UpdateMessageSendSucceeded -> {
                val p = uploads.remove(obj.oldMessageId)
                uploadFiles.values.remove(obj.oldMessageId)
                if (p != null && p.cont.isActive) p.cont.resume(obj.message)
            }
            is TdApi.UpdateMessageSendFailed -> {
                val p = uploads.remove(obj.oldMessageId)
                uploadFiles.values.remove(obj.oldMessageId)
                if (p != null && p.cont.isActive) {
                    p.cont.resumeWithException(TgUploadException("Telegram could not send this file.", false))
                    // Clean the failed local message so it is not retried behind our back.
                    val d = TdApi.DeleteMessages()
                    d.chatId = obj.message.chatId
                    d.messageIds = longArrayOf(obj.message.id)
                    d.revoke = true
                    client?.send(d) { }
                }
            }
            else -> {}
        }
    }

    // ---------------- Phase 4: uploads ----------------

    private val uploads = ConcurrentHashMap<Long, PendingUpload>()      // temporary message id -> waiting upload
    private val uploadFiles = ConcurrentHashMap<Int, Long>()            // TDLib file id -> temporary message id

    private fun onFileUpdate(f: TdApi.File) {
        val temp = uploadFiles[f.id] ?: return
        val p = uploads[temp] ?: return
        val total = f.size.toLong().takeIf { it > 0 } ?: f.expectedSize.toLong()
        if (total > 0) p.onProgress((f.remote.uploadedSize.toLong().toFloat() / total).coerceIn(0f, 1f))
    }

    /**
     * Sends a local file to the storage channel as an original-quality document and waits until Telegram has
     * really accepted it. Cancelling the coroutine cancels the upload (the pending message is deleted).
     */
    suspend fun uploadDocument(chatId: Long, path: String, caption: String, onProgress: (Float) -> Unit): TdApi.Message =
        suspendCancellableCoroutine { cont ->
            val c = client
            if (c == null) {
                cont.resumeWithException(TgUploadException("Telegram is not running.", false))
                return@suspendCancellableCoroutine
            }
            val input = TdApi.InputFileLocal()
            input.path = path
            val cap = TdApi.FormattedText()
            cap.text = caption
            cap.entities = emptyArray<TdApi.TextEntity>()
            val doc = TdApi.InputMessageDocument()
            doc.document = input
            doc.caption = cap
            doc.disableContentTypeDetection = true   // keep photos as untouched originals
            val q = TdApi.SendMessage()
            q.chatId = chatId
            q.inputMessageContent = doc
            c.send(q) { r ->
                when (r) {
                    is TdApi.Message -> {
                        val fid = (r.content as? TdApi.MessageDocument)?.document?.document?.id
                        uploads[r.id] = PendingUpload(onProgress, cont)
                        if (fid != null) uploadFiles[fid] = r.id
                        cont.invokeOnCancellation {
                            uploads.remove(r.id)
                            uploadFiles.values.remove(r.id)
                            val d = TdApi.DeleteMessages()
                            d.chatId = chatId
                            d.messageIds = longArrayOf(r.id)
                            d.revoke = true
                            client?.send(d) { }
                        }
                    }
                    is TdApi.Error -> if (cont.isActive) {
                        // Errors with code 400 mean the request itself is wrong (retrying cannot help).
                        cont.resumeWithException(TgUploadException(r.message ?: "Telegram error", r.code == 400))
                    }
                    else -> if (cont.isActive) cont.resumeWithException(TgUploadException("Unexpected Telegram answer.", false))
                }
            }
        }

    /** Reads a message back from Telegram (used to double-check an upload really exists). */
    suspend fun fetchMessage(chatId: Long, messageId: Long): TdApi.Message? =
        suspendCancellableCoroutine { cont ->
            val c = client
            if (c == null) { cont.resume(null); return@suspendCancellableCoroutine }
            val q = TdApi.GetMessage()
            q.chatId = chatId
            q.messageId = messageId
            c.send(q) { r -> if (cont.isActive) cont.resume(r as? TdApi.Message) }
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

    /** Removes the storage from this app only. The Telegram channel and its content are untouched. */
    fun forgetStorage() {
        StorageStore.clear(app)
        _storage.value = null
        _status.value = StorageStatus.Unknown
    }
}
