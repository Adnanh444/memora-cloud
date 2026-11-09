package com.adnan.memora.backup

import android.content.Context
import android.net.Uri
import com.adnan.memora.R
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.data.BackupState
import com.adnan.memora.gallery.BackupPhase
import com.adnan.memora.gallery.BackupSummary
import com.adnan.memora.gallery.ItemBackup
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.gallery.MediaRepository
import com.adnan.memora.telegram.StorageStatus
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.telegram.ThumbInfo
import com.adnan.memora.telegram.UploadResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileNotFoundException

fun LocalMedia.toEntry() = BackupEntry(
    uri = key, name = name, mime = mime, size = size, dateTaken = dateTaken, isVideo = isVideo,
    dir = bucket.ifBlank { null },
)

/** Caption format: `memora:v1 sha256=… taken=… dir=…`. Old messages without `dir` still parse. */
private fun captionFor(e: BackupEntry, sha: String): String {
    val base = "memora:v1 sha256=$sha taken=${e.dateTaken}"
    val dir = e.dir?.trim()?.replace(' ', '_') ?: return base
    return "$base dir=$dir"
}

/** A backed-up file as shown in the Cloud tab. The "uri" points at Telegram, not at the phone. */
fun BackupEntry.toCloudMedia() = LocalMedia(
    uri = CloudUri.thumb(chatId, messageId), name = name, mime = mime, size = size,
    dateTaken = dateTaken, dateModified = verifiedAt, width = 0, height = 0, durationMs = 0L,
    isVideo = isVideo, bucket = dir?.takeIf { it.isNotBlank() } ?: "Unfiled",
)

/**
 * Queue -> hash while copying -> duplicate check -> upload to Telegram -> verify.
 * RULE: a file is only ever uploaded if its type (photos/videos) AND its folder are turned ON in Backup settings.
 * Original files on the phone are never deleted or modified by backup.
 */
object BackupEngine {
    private const val MAX_AUTO_ATTEMPTS = 5
    private const val MAX_FILE_BYTES = 2_000_000_000L
    private const val TRASH_DAYS = 30L

    private lateinit var appCtx: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val uploadGate = UploadRunGate()
    private data class Live(val uri: String, val progress: Float, val stage: String)
    private val running = MutableStateFlow(false)
    @Volatile private var uploadJob: Job? = null
    private val live = MutableStateFlow<Live?>(null)
    private val configFlow = MutableStateFlow<BackupConfig?>(null)

    private val _summary = MutableStateFlow(BackupSummary())
    val summary: StateFlow<BackupSummary> = _summary
    private val _items = MutableStateFlow<Map<String, ItemBackup>>(emptyMap())
    val items: StateFlow<Map<String, ItemBackup>> = _items
    private val _cloud = MutableStateFlow<List<BackupEntry>>(emptyList())
    val cloud: StateFlow<List<BackupEntry>> = _cloud
    private val _cloudTrash = MutableStateFlow<List<BackupEntry>>(emptyList())
    val cloudTrash: StateFlow<List<BackupEntry>> = _cloudTrash

    @Synchronized
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
        scope.launch {
            dao.observeAll().collect { entries ->
                _cloud.value = entries.filter { it.state == BackupEntry.ST_VERIFIED && it.messageId != 0L }
                    .distinctBy { it.chatId to it.messageId }.sortedByDescending { it.dateTaken }
                _cloudTrash.value = entries.filter { it.state == BackupEntry.ST_TRASHED }
                    .distinctBy { it.chatId to it.messageId }.sortedByDescending { it.trashedAt }
            }
        }
    }

    private fun compute(
        entries: List<BackupEntry>, run: Boolean, lv: Live?, cfg: BackupConfig?, st: StorageStatus,
    ): Pair<BackupSummary, Map<String, ItemBackup>> {
        val map = HashMap<String, ItemBackup>(entries.size * 2)
        var done = 0
        var waiting = 0
        var total = 0
        var attention = 0
        var confirming = 0
        var currentName: String? = null
        var currentProgress = 0f
        for (e in entries) {
            if (e.state == BackupEntry.ST_TRASHED || e.state == BackupEntry.ST_DELETED) continue
            if (e.uri.startsWith("cloud:")) continue // restored from the cloud, not a file of this phone
            total++
            val isLive = lv != null && lv.uri == e.uri
            val item = when (e.state) {
                BackupEntry.ST_VERIFIED -> { done++; ItemBackup(BackupState.VERIFIED) }
                BackupEntry.ST_DUPLICATE -> { done++; ItemBackup(BackupState.VERIFIED, 0f, true) }
                BackupEntry.ST_UPLOADING -> {
                    if (isLive) { currentName = e.name; currentProgress = lv!!.progress } else waiting++
                    ItemBackup(BackupState.UPLOADING, if (isLive) lv!!.progress else 0f)
                }
                BackupEntry.ST_CONFIRMING -> {
                    if (isLive) { currentName = e.name; currentProgress = lv!!.progress; ItemBackup(BackupState.UPLOADING, lv.progress) }
                    else { attention++; confirming++; ItemBackup(BackupState.FAILED) }
                }
                BackupEntry.ST_FAILED -> { attention++; ItemBackup(BackupState.FAILED) }
                else -> { waiting++; ItemBackup(BackupState.QUEUED) }
            }
            map[e.uri] = item
        }
        val active = (cfg?.enabled == true) || total > 0
        val ready = st is StorageStatus.Verified
        val phase = when {
            !active || !ready -> BackupPhase.NOT_SET_UP
            cfg?.paused == true -> BackupPhase.PAUSED
            run -> BackupPhase.RUNNING
            waiting > 0 || attention > 0 -> BackupPhase.PAUSED
            else -> BackupPhase.IDLE
        }
        return BackupSummary(phase, done, total, currentName, currentProgress, cfg?.paused == true, waiting, attention, confirming, lv?.stage) to map
    }

    /** THE RULE. A file may upload only if its type is ON and its folder is ON. */
    fun isEligible(cfg: BackupConfig, isVideo: Boolean, bucket: String?): Boolean =
        bucket != null && bucket != "Memora" &&
            (if (isVideo) cfg.videos else cfg.photos) && bucket in cfg.folders

    /**
     * Pause. The file being uploaded right now is allowed to finish — so nothing is left
     * half-sent on the server and no temporary copy is abandoned — and the queue stops
     * before the next file starts.
     */
    fun pause(ctx: Context) {
        val cfg = BackupSettings.load(ctx).copy(paused = true)
        BackupSettings.save(ctx, cfg)
        configFlow.value = cfg
        // no cancel: the running loop sees paused == true between files and stops cleanly
    }

    /** Immediate stop. Used only where waiting is not acceptable; the cancelled file goes back to the queue. */
    fun pauseNow(ctx: Context) {
        val cfg = BackupSettings.load(ctx).copy(paused = true)
        BackupSettings.save(ctx, cfg)
        configFlow.value = cfg
        uploadJob?.cancel()
    }

    fun resume(ctx: Context) {
        val cfg = BackupSettings.load(ctx).copy(paused = false)
        BackupSettings.save(ctx, cfg)
        configFlow.value = cfg
        BackupScheduler.runNow(ctx, append = true)
    }

    fun updateConfig(ctx: Context, cfg: BackupConfig) {
        val old = configFlow.value ?: BackupSettings.load(ctx)
        BackupSettings.save(ctx, cfg)
        configFlow.value = cfg
        BackupScheduler.sync(ctx)
        val tightened = (old.photos && !cfg.photos) || (old.videos && !cfg.videos) ||
            (old.folders - cfg.folders).isNotEmpty()
        if (tightened) {
            // something was turned OFF: stop the running upload and drop everything that is no longer allowed
            uploadJob?.cancel()
            scope.launch {
                purgeIneligible(ctx, cfg)
                if (!cfg.paused) BackupScheduler.runNow(ctx, append = true)
            }
        } else if (cfg.enabled && !cfg.paused) {
            BackupScheduler.runNow(ctx)
        }
    }

    /** Removes queued/failed items whose type or folder is now OFF, so they can never upload. */
    suspend fun purgeIneligible(ctx: Context, cfg: BackupConfig = BackupSettings.load(ctx)) = withContext(Dispatchers.IO) {
        val dao = BackupDb.get(ctx).dao()
        val pending = dao.queuedOrFailed()
        if (pending.isEmpty()) return@withContext
        val media = MediaRepository(ctx).loadAll().associateBy { it.key }
        val bad = pending.filter { !isEligible(cfg, it.isVideo, media[it.uri]?.bucket) }.map { it.uri }
        bad.chunked(500).forEach { dao.deleteQueued(it) }
    }

    /** Finds new photos/videos in the allowed folders and adds them to the queue. */
    suspend fun scanAndEnqueue(ctx: Context): Int = withContext(Dispatchers.IO) {
        val cfg = BackupSettings.load(ctx)
        if (!cfg.enabled) return@withContext 0
        val dao = BackupDb.get(ctx).dao()
        val media = MediaRepository(ctx).loadAll()
        // photos already in the cloud (e.g. after a restore) are recognised by content and never uploaded again
        RecoveryEngine.matchLocal(ctx, media)
        val existing = dao.allKeys().toHashSet()
        val fresh = media.filter { m -> m.key !in existing && isEligible(cfg, m.isVideo, m.bucket) }.map { it.toEntry() }
        if (fresh.isNotEmpty()) dao.insertAll(fresh)
        fresh.size
    }

    /** Manual backup. Items whose type or folder is OFF are skipped. Returns (added, skipped). */
    suspend fun enqueueManual(ctx: Context, items: List<LocalMedia>): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val cfg = BackupSettings.load(ctx)
        val ok = items.filter { isEligible(cfg, it.isVideo, it.bucket) }
        val skipped = items.size - ok.size
        if (ok.isEmpty()) return@withContext Pair(0, skipped)
        val dao = BackupDb.get(ctx).dao()
        val existing = dao.allKeys().toHashSet()
        val fresh = ok.filter { it.key !in existing }.map { it.toEntry() }
        if (fresh.isNotEmpty()) dao.insertAll(fresh)
        ok.map { it.key }.chunked(500).forEach { dao.requeue(it); dao.requeueDeleted(it) }
        // KEEP would lose a manual enqueue racing the end of an already-running worker.
        BackupScheduler.runNow(ctx, append = true)
        Pair(ok.size, skipped)
    }

    suspend fun retryFailed(ctx: Context) = withContext(Dispatchers.IO) {
        BackupDb.get(ctx).dao().retryAllFailed()
        BackupScheduler.runNow(ctx, append = true)
    }

    suspend fun hasRetryableFailures(ctx: Context): Boolean = BackupDb.get(ctx).dao().retryableFailures() > 0

    /** Positive matches only. A missing/partial response never authorizes another upload. */
    suspend fun checkUnconfirmed(ctx: Context): Int = uploadGate.run {
        val dao = BackupDb.get(ctx).dao()
        val tg = TelegramClient.get(ctx)
        val pending = dao.unconfirmed()
        if (pending.isEmpty() || !tg.awaitUsable()) return@run 0
        var confirmed = 0
        for ((chat, entries) in pending.groupBy { it.chatId }) {
            val documents = try { withTimeoutOrNull(120_000L) { tg.scanHistory(chat) {} }?.docs }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null } ?: continue
            for (entry in entries) {
                if (entry.sha256 == null) continue
                val found = documents.firstOrNull { it.sha == entry.sha256 && it.size == entry.size }
                if (found != null) {
                    dao.update(entry.copy(state = BackupEntry.ST_VERIFIED, messageId = found.messageId,
                        verifiedAt = System.currentTimeMillis(), uploadedAt = found.dateMs, error = null))
                    confirmed++
                    continue
                }
                // The upload was cut off: no complete copy exists in the channel.
                // Delete whatever half-arrived message is there, then put the file back in the
                // queue so it is uploaded again from scratch — never a broken copy, never a duplicate.
                if (entry.messageId != 0L) {
                    withTimeoutOrNull(60_000L) { tg.deleteMessages(chat, listOf(entry.messageId)) }
                }
                dao.update(entry.copy(
                    state = BackupEntry.ST_QUEUED, messageId = 0L, sha256 = null,
                    attempts = 0, error = ctx.getString(R.string.rf_upload_partial_removed),
                ))
            }
        }
        confirmed
    }

    /** Explicit owner decision only, after inspecting the destination. May duplicate an unobserved remote copy. */
    suspend fun retryHeld(ctx: Context) = uploadGate.run {
        BackupDb.get(ctx).dao().releaseUnconfirmed()
        BackupScheduler.runNow(ctx, append = true)
    }

    // ------------------------------------------------------------------ cloud library / cloud bin

    /** "Delete from cloud" for items chosen in the Cloud tab: moves them to the cloud bin (30 days). */
    suspend fun trashCloudMessages(msgs: List<Pair<Long, Long>>) = withContext(Dispatchers.IO) {
        val dao = BackupDb.get(appCtx).dao()
        val now = System.currentTimeMillis()
        msgs.distinct().forEach { dao.trashByMessage(it.first, it.second, now) }
    }

    /**
     * Gives the pictures uploaded before folders existed their real folder back.
     * It looks the file up in the phone library and copies the folder name — nothing is uploaded,
     * downloaded or deleted, and nothing is written to Telegram. Safe to run on every start: it only
     * touches rows that still have no folder.
     */
    suspend fun backfillFolders(ctx: Context): Int = withContext(Dispatchers.IO) {
        val dao = BackupDb.get(ctx).dao()
        val todo = dao.all().filter { it.dir == null && it.uri.startsWith("content:") }
        if (todo.isEmpty()) return@withContext 0
        val byKey = MediaRepository(ctx).loadAll().associateBy { it.key }
        var fixed = 0
        for (e in todo) {
            val bucket = byKey[e.uri]?.bucket?.takeIf { it.isNotBlank() } ?: continue
            dao.update(e.copy(dir = bucket))
            fixed++
        }
        fixed
    }

    /**
     * Puts files into a folder. Only the record changes: no file is downloaded, re-uploaded or deleted.
     * New uploads carry the folder in their caption, so a reinstall rebuilds it from the channel.
     */
    suspend fun setFolder(ctx: Context, keys: List<String>, folder: String?) = withContext(Dispatchers.IO) {
        val dao = BackupDb.get(ctx).dao()
        val clean = folder?.trim()?.takeIf { it.isNotBlank() }
        keys.chunked(400).flatMap { dao.getMany(it) }.forEach { dao.update(it.copy(dir = clean)) }
    }

    /** "Delete from cloud" for photos chosen on the phone. */
    suspend fun trashCloudForLocal(ctx: Context, uris: List<String>) = withContext(Dispatchers.IO) {
        val dao = BackupDb.get(ctx).dao()
        val now = System.currentTimeMillis()
        uris.chunked(500).flatMap { dao.getMany(it) }
            .filter { it.messageId != 0L && (it.state == BackupEntry.ST_VERIFIED || it.state == BackupEntry.ST_DUPLICATE) }
            .map { it.chatId to it.messageId }.distinct()
            .forEach { dao.trashByMessage(it.first, it.second, now) }
    }

    suspend fun restoreCloud(msgs: List<Pair<Long, Long>>) = withContext(Dispatchers.IO) {
        val dao = BackupDb.get(appCtx).dao()
        msgs.distinct().forEach { dao.restoreByMessage(it.first, it.second) }
    }

    /** Permanently deletes the messages from Telegram. Returns true if Telegram confirmed. */
    suspend fun deleteCloudForever(ctx: Context, msgs: List<Pair<Long, Long>>): Boolean = withContext(Dispatchers.IO) {
        val dao = BackupDb.get(ctx).dao()
        val tg = TelegramClient.get(ctx)
        if (!tg.awaitUsable(30_000)) return@withContext false
        var all = true
        msgs.distinct().groupBy { it.first }.forEach { (chat, list) ->
            val ids = list.map { it.second }
            if (tg.deleteMessages(chat, ids)) ids.forEach { dao.tombstoneByMessage(chat, it) } else all = false
        }
        all
    }

    private suspend fun purgeExpiredCloudTrash(dao: BackupDao, tg: TelegramClient) {
        val expired = dao.expiredTrash(System.currentTimeMillis() - TRASH_DAYS * 24 * 3600 * 1000)
        expired.groupBy { it.chatId }.forEach { (chat, list) ->
            val ids = list.map { it.messageId }.distinct()
            if (tg.deleteMessages(chat, ids)) ids.forEach { dao.tombstoneByMessage(chat, it) }
        }
    }

    // ------------------------------------------------------------------ uploads

    /** Processes the queue one item at a time. Returns false if the cloud storage was not usable. */
    suspend fun runUploads(ctx: Context): Boolean = uploadGate.run {
        if (BackupSettings.load(ctx).paused) true else {
            scanAndEnqueue(ctx)
            runUploadsExclusive(ctx)
        }
    }

    private suspend fun runUploadsExclusive(ctx: Context): Boolean {
        withContext(Dispatchers.IO) { purgePartials(ctx) }
        val dao = BackupDb.get(ctx).dao()
        val tg = TelegramClient.get(ctx)
        if (!tg.awaitUsable()) return false
        val chatId = tg.storage.value?.chatId ?: return false
        // Safety rail: if the channel already holds a backup but this app has indexed nothing,
        // the duplicate check has nothing to compare against and every file would upload twice.
        // Stop, and tell the owner to rebuild the library first.
        if (dao.cloudEntries().isEmpty() && tg.channelHasBackup(chatId)) {
            live.value = Live("", 0f, ctx.getString(R.string.rf_index_missing))
            return false
        }
        running.value = true
        try {
            purgeExpiredCloudTrash(dao, tg)
            dao.resetUploading()
            val todo = dao.pendingUris(MAX_AUTO_ATTEMPTS)
            val media = MediaRepository(ctx).loadAll().associateBy { it.key }
            coroutineScope {
                val job = launch(start = CoroutineStart.LAZY) {
                    for (uri in todo) {
                        val cfg = BackupSettings.load(ctx)
                        if (cfg.paused) break
                        val e = dao.get(uri) ?: continue
                        // the rule is checked again right before every single upload
                        if (!isEligible(cfg, e.isVideo, media[uri]?.bucket)) { dao.delete(uri); continue }
                        processOne(ctx, dao, tg, chatId, uri)
                    }
                }
                uploadJob = job
                job.start()
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

    /** Deletes temporary copies left behind by a cancelled or killed upload. */
    fun purgePartials(ctx: Context) {
        val dir = File(ctx.cacheDir, "upload")
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { if (!it.delete()) it.deleteOnExit() }
    }

    private suspend fun processOne(ctx: Context, dao: BackupDao, tg: TelegramClient, chatId: Long, uri: String) {
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
        live.value = Live(e.uri, 0f, ctx.getString(R.string.rf_upload_stage_prepare))
        var workspace: UploadWorkspace? = null
        try {
            val attempt = UploadWorkspace.create(dir, e.name)
            workspace = attempt
            val temp = attempt.original
            val thumbFile = attempt.thumbnail
            val sha = try {
                Hashing.copyAndHash(ctx, Uri.parse(e.uri), temp)
            } catch (_: FileNotFoundException) {
                dao.delete(e.uri)
                return
            }
            val actualSize = temp.length()

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

            val dims = Thumbs.make(ctx, Uri.parse(e.uri), e.isVideo, thumbFile)
            val thumb = if (dims != null) ThumbInfo(thumbFile.absolutePath, dims.first, dims.second) else null

            // Persist uncertainty BEFORE sending: even process death cannot silently requeue this attempt.
            e = e.copy(sha256 = sha, size = actualSize, chatId = chatId, state = BackupEntry.ST_CONFIRMING)
            dao.update(e)
            live.value = Live(e.uri, 0f, ctx.getString(R.string.rf_upload_stage_send))
            var lastReported = 0f
            val res = tg.uploadDocument(chatId, temp.absolutePath, captionFor(e, sha), thumb) { p ->
                if (p - lastReported >= 0.05f || p >= 1f) { lastReported = p; live.value = Live(e.uri, p, ctx.getString(R.string.rf_upload_stage_send)) }
            }
            val now = System.currentTimeMillis()
            when (res) {
                is UploadResult.Success -> {
                    e = e.copy(messageId = res.messageId)
                    dao.update(e)
                    live.value = Live(e.uri, 1f, ctx.getString(R.string.rf_upload_stage_verify))
                    if (tg.verifyMessage(chatId, res.messageId, actualSize)) {
                        dao.update(
                            e.copy(
                                sha256 = sha, state = BackupEntry.ST_VERIFIED, chatId = chatId, messageId = res.messageId,
                                uploadedAt = now, verifiedAt = now, error = null,
                            )
                        )
                    } else {
                        dao.update(e.copy(sha256 = sha, state = BackupEntry.ST_CONFIRMING, error = ctx.getString(R.string.rf_upload_uncertain)))
                    }
                }
                is UploadResult.Uncertain -> dao.update(e.copy(state = BackupEntry.ST_CONFIRMING, messageId = res.messageId,
                    error = ctx.getString(R.string.rf_upload_uncertain)))
                is UploadResult.Failure -> dao.update(e.copy(sha256 = sha, state = BackupEntry.ST_FAILED, error = res.message))
            }
        } catch (c: CancellationException) {
            withContext(NonCancellable) {
                dao.update(if (e.state == BackupEntry.ST_CONFIRMING) e.copy(error = ctx.getString(R.string.rf_upload_uncertain))
                    else e.copy(state = BackupEntry.ST_QUEUED))
            }
            throw c
        } catch (t: Exception) {
            dao.update(e.copy(state = if (e.state == BackupEntry.ST_CONFIRMING) BackupEntry.ST_CONFIRMING else BackupEntry.ST_FAILED,
                error = if (e.state == BackupEntry.ST_CONFIRMING) ctx.getString(R.string.rf_upload_uncertain) else (t.message ?: "error").take(120)))
        } finally {
            workspace?.close()
        }
    }
}
