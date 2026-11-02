package com.adnan.memora.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.adnan.memora.data.BackupState
import com.adnan.memora.data.MediaItemEntity
import com.adnan.memora.data.MemoraDb
import com.adnan.memora.gallery.BackupPhase
import com.adnan.memora.gallery.BackupSummary
import com.adnan.memora.gallery.ItemBackup
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.gallery.MediaRepository
import com.adnan.memora.telegram.StorageStatus
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.telegram.TgAuth
import com.adnan.memora.telegram.TgUploadException
import com.adnan.memora.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * The backup engine: a persistent upload queue (Room) worked off by WorkManager.
 * An item is only ever marked VERIFIED after Telegram accepted the file AND the message was read back
 * with the exact same size.
 */
class BackupEngine private constructor(private val app: Context) {
    companion object {
        const val MAX_ATTEMPTS = 5
        private const val UPLOAD_WORK = "memora-upload"
        private const val SCAN_WORK = "memora-scan"
        /** Telegram accepts files up to 2 GB for normal accounts. */
        private const val MAX_BYTES = 2_000_000_000L

        @Volatile private var inst: BackupEngine? = null
        fun get(ctx: Context): BackupEngine =
            inst ?: synchronized(this) { inst ?: BackupEngine(ctx.applicationContext).also { inst = it } }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val db get() = MemoraDb.get(app)
    private val dao get() = db.mediaDao()
    private val prefs = BackupPrefs(app)
    private val tg = TelegramClient.get(app)

    private val running = MutableStateFlow(false)
    private val progress = MutableStateFlow<Pair<String, Float>?>(null)   // local uri -> 0..1
    private val currentName = MutableStateFlow<String?>(null)

    val autoFolders = MutableStateFlow(prefs.autoFolders())
    val wifiOnly = MutableStateFlow(prefs.wifiOnly())

    /** Per thumbnail badge: local uri -> state. */
    val items: StateFlow<Map<String, ItemBackup>> =
        combine(dao.observeBackupRows(), progress) { rows, prog ->
            val out = HashMap<String, ItemBackup>(rows.size * 2)
            for (r in rows) {
                val u = r.localUri ?: continue
                out[u] = ItemBackup(r.state, if (r.state == BackupState.UPLOADING && prog?.first == u) (prog?.second ?: 0f) else 0f)
            }
            out as Map<String, ItemBackup>
        }.stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** The strip at the top of the Photos tab. */
    val summary: StateFlow<BackupSummary> =
        combine(dao.observeBackupRows(), running, progress, currentName, tg.storage) { rows, run, prog, name, storage ->
            var queued = 0; var uploading = 0; var ok = 0; var failed = 0
            for (r in rows) when (r.state) {
                BackupState.QUEUED -> queued++
                BackupState.UPLOADING -> uploading++
                BackupState.VERIFIED -> ok++
                BackupState.FAILED -> failed++
                else -> {}
            }
            val pending = queued + uploading
            val phase = when {
                storage == null -> BackupPhase.NOT_SET_UP
                pending > 0 && run -> BackupPhase.RUNNING
                pending > 0 -> BackupPhase.PAUSED
                else -> BackupPhase.IDLE
            }
            BackupSummary(phase, ok, pending + ok + failed, if (phase == BackupPhase.RUNNING) name else null, prog?.second ?: 0f, failed)
        }.stateIn(scope, SharingStarted.Eagerly, BackupSummary())

    /** True when a storage channel is set up on this phone. */
    fun isSetUp(): Boolean = tg.storage.value != null

    // ---------------------------------------------------------------- queueing

    /** Puts these photos/videos in the upload queue (skips ones already backed up). */
    fun enqueue(media: List<LocalMedia>) {
        if (media.isEmpty()) return
        scope.launch {
            addToQueue(media, retryKnown = true)
            kick()
        }
    }

    private suspend fun addToQueue(media: List<LocalMedia>, retryKnown: Boolean) {
        db.withTransaction {
            for (m in media) {
                val uri = m.uri.toString()
                val row = MediaItemEntity(
                    localUri = uri, displayName = m.name, mimeType = m.mime, sizeBytes = m.size,
                    dateTaken = m.dateTaken, lastModified = m.dateModified, state = BackupState.QUEUED,
                )
                if (dao.insertIgnore(row) == -1L && retryKnown) {
                    val old = dao.findByUri(uri)
                    if (old != null && (old.state == BackupState.FAILED || old.state == BackupState.NOT_BACKED_UP)) {
                        dao.update(old.copy(state = BackupState.QUEUED, attempts = 0, errorMessage = null))
                    }
                }
            }
        }
    }

    fun retryFailed() {
        scope.launch { dao.requeueFailed(); kick() }
    }

    /** Starts (or continues) uploading. Safe to call any time. */
    fun kick(replace: Boolean = false) {
        val net = if (wifiOnly.value) NetworkType.UNMETERED else NetworkType.CONNECTED
        val req = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(net).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(app)
            .enqueueUniqueWork(UPLOAD_WORK, if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, req)
    }

    // ---------------------------------------------------------------- settings

    fun setWifiOnly(v: Boolean) {
        prefs.setWifiOnly(v)
        wifiOnly.value = v
        scope.launch { if (hasPending()) kick(replace = true) }
    }

    fun toggleAutoFolder(name: String) {
        val set = autoFolders.value.toMutableSet()
        if (!set.add(name)) set.remove(name)
        prefs.setAutoFolders(set)
        autoFolders.value = set
        schedule()
        if (name in set) scope.launch { scanFolders(); kick() }
    }

    /** Keeps the periodic folder check in line with the settings. Called at app start and when folders change. */
    fun schedule() {
        val wm = WorkManager.getInstance(app)
        if (autoFolders.value.isEmpty()) {
            wm.cancelUniqueWork(SCAN_WORK)
        } else {
            val req = PeriodicWorkRequestBuilder<ScanWorker>(30, TimeUnit.MINUTES).build()
            wm.enqueueUniquePeriodicWork(SCAN_WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
        scope.launch { if (hasPending()) kick() }
    }

    private suspend fun hasPending(): Boolean = dao.nextQueued() != null

    /** Adds new photos/videos of the auto-backup folders to the queue. Returns how many folders are watched. */
    suspend fun scanFolders() {
        val folders = autoFolders.value
        if (folders.isEmpty()) return
        val all = MediaRepository(app).loadAll().filter { it.bucket in folders }
        addToQueue(all, retryKnown = false)
    }

    // ---------------------------------------------------------------- the upload loop

    /** Returns true if WorkManager should try again later (network trouble, Telegram not ready). */
    suspend fun runQueue(onItem: suspend (String) -> Unit): Boolean {
        if (!running.compareAndSet(false, true)) return false
        try {
            tg.start()
            val ready = withTimeoutOrNull(90_000) {
                tg.auth.first { it is TgAuth.Ready || it is TgAuth.NeedPhone || it is TgAuth.NeedCode ||
                    it is TgAuth.NeedPassword || it is TgAuth.NeedCredentials || it is TgAuth.Failed }
            } ?: return true                                    // Telegram is slow to start: try again later
            if (ready !is TgAuth.Ready) return false             // signed out: the user has to sign in again
            val status = withTimeoutOrNull(60_000) {
                tg.storageStatus.first { it is StorageStatus.Verified || it is StorageStatus.Problem }
            } ?: return true
            if (status !is StorageStatus.Verified) return false  // channel problem: shown on the Telegram screen
            val chatId = tg.storage.value?.chatId ?: return false

            dao.resetUploading()
            while (coroutineContext.isActive) {
                val item = dao.nextQueued() ?: break
                onItem(item.displayName)
                val retryLater = uploadOne(item, chatId)
                if (retryLater) return true
            }
            return false
        } finally {
            progress.value = null
            currentName.value = null
            running.value = false
        }
    }

    /** Uploads one item. Returns true if the whole run should stop and be retried later. */
    private suspend fun uploadOne(item: MediaItemEntity, chatId: Long): Boolean {
        val key = item.localUri ?: run { dao.update(item.copy(state = BackupState.FAILED, errorMessage = "No file")); return false }
        currentName.value = item.displayName
        progress.value = key to 0f
        dao.update(item.copy(state = BackupState.UPLOADING, errorMessage = null))
        val dir = File(app.cacheDir, "upload/${item.id}")
        try {
            // Copy to a private temporary file: TDLib needs a real path, and the original is never touched.
            val file = File(dir, item.displayName.replace('/', '_').ifBlank { "file_${item.id}" })
            withContext(Dispatchers.IO) {
                dir.mkdirs()
                val input = app.contentResolver.openInputStream(Uri.parse(key)) ?: throw FileNotFoundException(key)
                input.use { ins -> file.outputStream().use { out -> ins.copyTo(out, 256 * 1024) } }
            }
            val size = file.length()
            if (size <= 0L) throw FileNotFoundException(key)
            if (size > MAX_BYTES) { fail(item, app.getString(R.string.backup_err_too_big), permanent = true); return false }

            var last = 0f
            val caption = "memora:v1\nname=${item.displayName}\ntaken=${item.dateTaken}\nmime=${item.mimeType}"
            val sent = tg.uploadDocument(chatId, file.absolutePath, caption) { p ->
                if (abs(p - last) >= 0.02f) { last = p; progress.value = key to p }
            }

            // Verify: read the message back and compare the size. Only then is the item "backed up".
            val back = tg.fetchMessage(chatId, sent.id)
            val content = back?.content as? TdApi.MessageDocument
            if (content == null || content.document.document.size.toLong() != size) {
                return failTransient(item, app.getString(R.string.backup_err_verify))
            }
            val now = System.currentTimeMillis()
            dao.update(
                item.copy(
                    state = BackupState.VERIFIED, sizeBytes = size, telegramChatId = chatId, telegramMessageId = sent.id,
                    uploadDate = now, verifiedAt = now, attempts = 0, errorMessage = null,
                )
            )
            return false
        } catch (e: CancellationException) {
            withContext(NonCancellable) { dao.update(item.copy(state = BackupState.QUEUED)) }
            throw e
        } catch (e: FileNotFoundException) {
            fail(item, app.getString(R.string.backup_err_missing), permanent = true); return false
        } catch (e: SecurityException) {
            fail(item, app.getString(R.string.backup_err_missing), permanent = true); return false
        } catch (e: TgUploadException) {
            if (e.permanent) { fail(item, e.message ?: "", permanent = true); return false }
            return failTransient(item, e.message ?: "")
        } catch (e: java.io.IOException) {
            return failTransient(item, e.message ?: "")   // e.g. phone storage full while copying
        } finally {
            withContext(NonCancellable) { dir.deleteRecursively() }
            progress.value = null
        }
    }

    private suspend fun fail(item: MediaItemEntity, msg: String, permanent: Boolean) {
        dao.update(item.copy(state = BackupState.FAILED, errorMessage = msg, attempts = if (permanent) MAX_ATTEMPTS else item.attempts + 1))
    }

    /** A problem that may go away (network, Telegram busy). Item goes back in the queue, up to MAX_ATTEMPTS tries. */
    private suspend fun failTransient(item: MediaItemEntity, msg: String): Boolean {
        val n = item.attempts + 1
        if (n >= MAX_ATTEMPTS) dao.update(item.copy(state = BackupState.FAILED, attempts = n, errorMessage = msg))
        else dao.update(item.copy(state = BackupState.QUEUED, attempts = n, errorMessage = msg))
        return true   // stop this run; WorkManager retries later with a growing delay
    }
}
