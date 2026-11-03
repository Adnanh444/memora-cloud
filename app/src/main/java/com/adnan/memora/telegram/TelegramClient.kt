package com.adnan.memora.telegram

import android.content.Context
import android.os.Build
import com.adnan.memora.BuildConfig
import com.adnan.memora.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
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
}

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

    private suspend fun sendAndWait(q: TdApi.Function<*>): TdApi.Object? {
        val c = client ?: return null
        return suspendCancellableCoroutine<TdApi.Object?> { cont ->
            c.send(q) { r -> if (cont.isActive) cont.resume(r) }
        }
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
    suspend fun uploadDocument(chatId: Long, path: String, caption: String, onProgress: (Float) -> Unit): UploadResult {
        val file = TdApi.InputFileLocal()
        file.path = path
        val text = TdApi.FormattedText()
        text.text = caption
        text.entities = emptyArray()
        val content = TdApi.InputMessageDocument()
        content.document = file
        content.caption = text
        content.disableContentTypeDetection = true
        val req = TdApi.SendMessage()
        req.chatId = chatId
        req.inputMessageContent = content

        val sent = sendAndWait(req) ?: return UploadResult.Failure(app.getString(R.string.bk_err_send_failed))
        if (sent is TdApi.Error) return UploadResult.Failure(friendly(sent.message ?: ""))
        val msg = sent as? TdApi.Message ?: return UploadResult.Failure(app.getString(R.string.bk_err_send_failed))
        val temp = msg.id
        val fileId = (msg.content as? TdApi.MessageDocument)?.document?.document?.id
        if (fileId != null) progressByFile[fileId] = onProgress
        val deferred = deferredFor(temp)
        try {
            return deferred.await()
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
        val m = sendAndWait(q) as? TdApi.Message ?: return false
        val f = (m.content as? TdApi.MessageDocument)?.document?.document ?: return false
        return f.size == expectedSize || f.expectedSize == expectedSize
    }

    /** Removes the storage from this app only. The Telegram channel and its content are untouched. */
    fun forgetStorage() {
        StorageStore.clear(app)
        _storage.value = null
        _status.value = StorageStatus.Unknown
    }
}
