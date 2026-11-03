package com.adnan.memora.backup

import android.content.Context
import android.net.Uri
import com.adnan.memora.R
import com.adnan.memora.data.BackupState
import com.adnan.memora.gallery.BackupPhase
import com.adnan.memora.gallery.BackupSummary
import com.adnan.memora.gallery.ItemBackup
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.gallery.MediaRepository
import com.adnan.memora.telegram.StorageStatus
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.telegram.UploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

fun LocalMedia.toEntry() = BackupEntry(
    uri = key, name = name, mime = mime, size = size, dateTaken = dateTaken, isVideo = isVideo,
)

/**
 * The backup engine: queue -> hash while copying -> duplicate check -> upload to Telegram -> verify.
 * Original files on the phone are NEVER deleted or modified by backup.
 */
object BackupEngine {
    private const val MAX_AUTO_ATTEMPTS = 5
    private const val MAX_FILE_BYTES = 2_000_000_000L // Telegram's limit for normal accounts

    private lateinit var appCtx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val running = MutableStateFlow(false)
    @Volatile private var uploadJob: Job? = null
    private val live = MutableStateFlow<Pair<String, Float>?>(null)
    private val configFlow = MutableStateFlow<BackupConfig?>(null)

    private val _summary = MutableStateFlow(BackupSummary())
    val summary: StateFlow<BackupSummary> = _summary
    private val _items = MutableStateFlow<Map<String, ItemBackup>>(emptyMap())
    val items: StateFlow<Map<String, ItemBackup>> = _items

    fun init(ctx: Context) {
        if (::appCtx.isInitialized) return
        appCtx = ctx.applicationContext
        configFlow.value = BackupSettings.load(appCtx)
        val dao = BackupDb.get(appCtx).dao()
        val tg = TelegramClient.get(appCtx)
        scope.launch {
            combine(dao.observeAll(), running, live, configFlow, tg.storageStatus) { entries, run, lv, cfg, st ->
                compute(entries, run, lv, cfg, st)
            }.collect { (sum, map) ->
                _summary.value = sum
                _items.value = map
            }
        }
    }

    private fun compute(
        entries: List<BackupEntry>, run: Boolean, lv: Pair<String, Float>?, cfg: BackupConfig?, st: StorageStatus,
    ): Pair<BackupSummary, Map<String, ItemBackup>> {
        val map = HashMap<String, ItemBackup>(entries.size * 2)
        var done = 0
        var waiting = 0
        var currentName: String? = null
        var currentProgress = 0f
        for (e in entries) {
            val isLive = lv != null && lv.first == e.uri
            val item = when (e.state) {
                BackupEntry.ST_VERIFIED -> { done++; ItemBackup(BackupState.VERIFIED) }
                BackupEntry.ST_DUPLICATE -> { done++; ItemBackup(BackupState.VERIFIED, 0f, true) }
                BackupEntry.ST_UPLOADING -> {
                    if (isLive) { currentName = e.name; currentProgress = lv!!.second } else waiting++
                    ItemBackup(BackupState.UPLOADING, if (isLive) lv!!.second else 0f)
                }
                BackupEntry.ST_FAILED -> ItemBackup(BackupState.FAILED)
                else -> { waiting++; ItemBackup(BackupState.QUEUED) }
            }
            map[e.uri] = item
        }
        val active = (cfg?.enabled == true) || entries.isNotEmpty()
        val ready = st is StorageStatus.Verified
        val phase = when {
            !active || !ready -> BackupPhase.NOT_SET_UP
            cfg?.paused == true -> BackupPhase.PAUSED
            run -> BackupPhase.RUNNING
            waiting > 0 -> BackupPhase.PAUSED
            else -> BackupPhase.IDLE
        }
        return BackupSummary(phase, done, entries.size, currentName, currentProgress, cfg?.paused == true, waiting) to map
    }

    /** Stops the upload that is running RIGHT NOW (it goes back to the queue) and keeps backup paused. */
    fun pause(ctx: Context) {
        val cfg = BackupSettings.load(ctx).copy(paused = true)
        BackupSettings.save(ctx, cfg)
        configFlow.value = cfg
        uploadJob?.cancel()
    }

    /** Resumes backup from where the queue left off. */
    fun resume(ctx: Context) {
        val cfg = BackupSettings.load(ctx).copy(paused = false)
        BackupSettings.save(ctx, cfg)
        configFlow.value = cfg
        BackupScheduler.runNow(ctx, append = true)
    }

    fun updateConfig(ctx: Context, cfg: BackupConfig) {
        BackupSettings.save(ctx, cfg)
        configFlow.value = cfg
        BackupScheduler.sync(ctx)
        if (cfg.enabled && !cfg.paused) BackupScheduler.runNow(ctx)
    }

    /** Finds new photos/videos in the chosen folders and adds them to the queue. */
    suspend fun scanAndEnqueue(ctx: Context): Int = withContext(Dispatchers.IO) {
        val cfg = BackupSettings.load(ctx)
        if (!cfg.enabled) return@withContext 0
        val dao = BackupDb.get(ctx).dao()
        val media = MediaRepository(ctx).loadAll()
        val existing = dao.allKeys().toHashSet()
        val fresh = media.filter { m ->
            m.key !in existing && (if (m.isVideo) cfg.videos else cfg.photos) && m.bucket in cfg.folders
        }.map { it.toEntry() }
        if (fresh.isNotEmpty()) dao.insertAll(fresh)
        fresh.size
    }

    /** User picked specific items to back up now (ignores folder settings). */
    suspend fun enqueueManual(ctx: Context, items: List<LocalMedia>): Int = withContext(Dispatchers.IO) {
        val dao = BackupDb.get(ctx).dao()
        val existing = dao.allKeys().toHashSet()
        val fresh = items.filter { it.key !in existing }.map { it.toEntry() }
        if (fresh.isNotEmpty()) dao.insertAll(fresh)
        items.map { it.key }.chunked(500).forEach { dao.requeue(it) }
        BackupScheduler.runNow(ctx)
        fresh.size
    }

    suspend fun retryFailed(ctx: Context) = withContext(Dispatchers.IO) {
        BackupDb.get(ctx).dao().retryAllFailed()
        BackupScheduler.runNow(ctx)
    }

    /** Processes the queue one item at a time. Returns false if Telegram storage was not usable. */
    suspend fun runUploads(ctx: Context): Boolean {
        val dao = BackupDb.get(ctx).dao()
        val tg = TelegramClient.get(ctx)
        if (!tg.awaitUsable()) return false
        val chatId = tg.storage.value?.chatId ?: return false
        running.value = true
        try {
            dao.resetUploading()
            val todo = dao.pendingUris(MAX_AUTO_ATTEMPTS)
            coroutineScope {
                val job = launch {
                    for (uri in todo) {
                        if (BackupSettings.load(ctx).paused) break
                        processOne(ctx, dao, tg, chatId, uri)
                    }
                }
                uploadJob = job
                job.join()
            }
        } finally {
            withContext(NonCancellable) {
                uploadJob = null
                running.value = false
                live.value = null
            }
        }
        return true
    }

    private fun safeName(n: String): String {
        val cleaned = n.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_").trim().take(120)
        return if (cleaned.isBlank()) "file" else cleaned
    }

    private suspend fun processOne(
        ctx: Context, dao: BackupDao, tg: com.adnan.memora.telegram.TelegramClient, chatId: Long, uri: String,
    ) {
        var e = dao.get(uri) ?: return
        if (e.state == BackupEntry.ST_VERIFIED || e.state == BackupEntry.ST_DUPLICATE) return
        if (e.size > MAX_FILE_BYTES) {
            dao.update(e.copy(state = BackupEntry.ST_FAILED, attempts = 99, error = ctx.getString(R.string.bk_err_too_large)))
            return
        }
        val dir = File(ctx.cacheDir, "upload").apply { mkdirs() }
        if (dir.usableSpace < e.size + 50L * 1024 * 1024) {
            dao.update(e.copy(state = BackupEntry.ST_FAILED, attempts = e.attempts + 1, error = ctx.getString(R.string.bk_err_space)))
            return
        }
        e = e.copy(state = BackupEntry.ST_UPLOADING, error = null, attempts = e.attempts + 1)
        dao.update(e)
        live.value = e.uri to 0f
        val temp = File(dir, safeName(e.name))
        try {
            // 1) copy to a temporary file while computing SHA-256 (streaming)
            val sha = try {
                Hashing.copyAndHash(ctx, Uri.parse(e.uri), temp)
            } catch (_: FileNotFoundException) {
                dao.delete(e.uri) // file no longer exists on the phone
                return
            }
            val actualSize = temp.length()

            // 2) duplicate check against everything already backed up
            val twin = dao.findVerifiedByHash(sha, e.uri)
            if (twin != null) {
                val now = System.currentTimeMillis()
                dao.update(
                    e.copy(
                        sha256 = sha, state = BackupEntry.ST_DUPLICATE, duplicateOf = twin.uri,
                        chatId = twin.chatId, messageId = twin.messageId, verifiedAt = now, error = null,
                    )
                )
                return
            }

            // 3) upload as a document (original quality)
            var lastReported = 0f
            val res = tg.uploadDocument(chatId, temp.absolutePath, "memora:v1 sha256=$sha") { p ->
                if (p - lastReported >= 0.05f || p >= 1f) { lastReported = p; live.value = e.uri to p }
            }
            val now = System.currentTimeMillis()
            when (res) {
                is UploadResult.Success -> {
                    // 4) verify with Telegram before calling it backed up
                    if (tg.verifyMessage(chatId, res.messageId, actualSize)) {
                        dao.update(
                            e.copy(
                                sha256 = sha, state = BackupEntry.ST_VERIFIED, chatId = chatId, messageId = res.messageId,
                                uploadedAt = now, verifiedAt = now, error = null,
                            )
                        )
                    } else {
                        dao.update(e.copy(sha256 = sha, state = BackupEntry.ST_FAILED, error = ctx.getString(R.string.bk_err_verify)))
                    }
                }
                is UploadResult.Failure -> dao.update(e.copy(sha256 = sha, state = BackupEntry.ST_FAILED, error = res.message))
            }
        } catch (c: CancellationException) {
            withContext(NonCancellable) { dao.update(e.copy(state = BackupEntry.ST_QUEUED)) }
            throw c
        } catch (t: Exception) {
            dao.update(e.copy(state = BackupEntry.ST_FAILED, error = (t.message ?: "error").take(120)))
        } finally {
            temp.delete()
        }
    }
}
