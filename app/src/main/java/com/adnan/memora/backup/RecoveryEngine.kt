package com.adnan.memora.backup

import android.content.Context
import android.webkit.MimeTypeMap
import com.adnan.memora.R
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.gallery.MediaRepository
import com.adnan.memora.telegram.StorageCandidate
import com.adnan.memora.telegram.TelegramClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class RecState {
    object Idle : RecState()
    object Searching : RecState()
    class Found(val list: List<StorageCandidate>) : RecState()
    object NoneFound : RecState()
    class Restoring(val found: Int) : RecState()
    class Matching(val done: Int, val total: Int) : RecState()
    class Done(val recovered: Int, val matched: Int) : RecState()
    class Failed(val message: String) : RecState()
}

/**
 * Rebuilds the Cloud library after reinstalling the app or changing phones.
 * It reads the storage channel itself (every backed-up file carries its fingerprint), so nothing else is needed
 * except signing in to the same Telegram account.
 */
object RecoveryEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<RecState>(RecState.Idle)
    val state: StateFlow<RecState> = _state

    fun reset() {
        val s = _state.value
        if (s !is RecState.Searching && s !is RecState.Restoring && s !is RecState.Matching) _state.value = RecState.Idle
    }

    /** Step 1: find the Memora storage channel(s) in the signed-in Telegram account. */
    fun findBackups(ctx: Context) {
        val app = ctx.applicationContext
        _state.value = RecState.Searching
        scope.launch {
            val tg = TelegramClient.get(app)
            if (!tg.awaitSignedIn()) { _state.value = RecState.Failed(app.getString(R.string.rc_err_signin)); return@launch }
            val list = try { tg.findStorageCandidates() } catch (t: Exception) { emptyList() }
            _state.value = if (list.isEmpty()) RecState.NoneFound else RecState.Found(list)
        }
    }

    /** Step 2: use the chosen storage and rebuild the library from it. */
    fun restore(ctx: Context, candidate: StorageCandidate) {
        val app = ctx.applicationContext
        _state.value = RecState.Restoring(0)
        scope.launch {
            val tg = TelegramClient.get(app)
            tg.adoptStorage(candidate)
            // the storage must be reachable and owned by this account before recovery can be called successful
            if (!tg.awaitUsable(60_000)) {
                _state.value = RecState.Failed(app.getString(R.string.rc_err_verify))
                return@launch
            }
            rebuild(app, candidate.chatId)
        }
    }

    /** Re-reads the current storage (useful after clearing data or to double-check the library). */
    fun rebuildCurrent(ctx: Context) {
        val app = ctx.applicationContext
        _state.value = RecState.Restoring(0)
        scope.launch {
            val tg = TelegramClient.get(app)
            if (!tg.awaitUsable(60_000)) { _state.value = RecState.Failed(app.getString(R.string.rc_err_verify)); return@launch }
            val chat = tg.storage.value?.chatId
            if (chat == null) { _state.value = RecState.Failed(app.getString(R.string.rc_err_verify)); return@launch }
            rebuild(app, chat)
        }
    }

    private fun isVideo(mime: String, name: String): Boolean =
        mime.startsWith("video/") || name.substringAfterLast('.', "").lowercase() in setOf("mp4", "mov", "mkv", "webm", "3gp", "avi", "m4v")

    private fun fixMime(mime: String, name: String): String {
        if (mime != "application/octet-stream") return mime
        val ext = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: mime
    }

    private suspend fun rebuild(app: Context, chatId: Long) {
        try {
            val tg = TelegramClient.get(app)
            val dao = BackupDb.get(app).dao()
            val docs = tg.scanHistory(chatId) { _state.value = RecState.Restoring(it) }

            val known = dao.all().filter { it.messageId != 0L }.map { it.chatId to it.messageId }.toHashSet()
            val seenSha = HashSet<String>()
            val now = System.currentTimeMillis()
            val entries = ArrayList<BackupEntry>()
            for (d in docs.sortedBy { it.messageId }) {            // oldest first: keep the earliest copy of identical files
                if ((d.chatId to d.messageId) in known) continue
                val key = d.sha ?: "id:${d.messageId}"
                if (!seenSha.add(key)) continue
                val mime = fixMime(d.mime, d.name)
                entries.add(
                    BackupEntry(
                        uri = "cloud:${d.chatId}:${d.messageId}", name = d.name, mime = mime, size = d.size,
                        dateTaken = d.takenMs, isVideo = isVideo(mime, d.name), sha256 = d.sha,
                        state = BackupEntry.ST_VERIFIED, chatId = d.chatId, messageId = d.messageId,
                        uploadedAt = d.dateMs, verifiedAt = now,
                    )
                )
            }
            if (entries.isNotEmpty()) dao.insertAll(entries)

            // compare with the photos on THIS phone so they are not uploaded again
            _state.value = RecState.Matching(0, 0)
            val media = MediaRepository(app).loadAll()
            val matched = matchLocal(app, media) { done, total -> _state.value = RecState.Matching(done, total) }
            try {
                val storageId = tg.storage.value?.storageId
                if (storageId != null) com.adnan.memora.org.MetaSync.restore(app, chatId, storageId)
            } catch (_: Exception) {
                // favorites/albums could not be read back; the photos themselves are restored
            }
            _state.value = RecState.Done(entries.size, matched)
        } catch (t: Exception) {
            _state.value = RecState.Failed(app.getString(R.string.rc_failed_generic))
        }
    }

    /**
     * Finds local files whose content is already in the cloud (by SHA-256) and marks them as backed up.
     * Only files with the same size as a cloud file are hashed, and hashes are cached.
     */
    suspend fun matchLocal(
        ctx: Context, media: List<LocalMedia>, onProgress: ((Int, Int) -> Unit)? = null,
    ): Int = withContext(Dispatchers.IO) {
        val dao = BackupDb.get(ctx).dao()
        val cloud = dao.cloudEntries()
        if (cloud.isEmpty()) return@withContext 0
        val sizes = cloud.map { it.size }.toHashSet()
        val bySha = cloud.filter { it.sha256 != null }.associateBy { it.sha256!! }
        val existing = dao.allKeys().toHashSet()
        val candidates = media.filter { it.key !in existing && it.size in sizes }
        var matched = 0
        val now = System.currentTimeMillis()
        candidates.forEachIndexed { i, m ->
            try {
                val cached = dao.cachedHash(m.key)
                val h = if (cached != null && cached.size == m.size && cached.modified == m.dateModified) cached.sha256
                else Hashing.hashOnly(ctx, m.uri).also { dao.putHash(HashCacheEntry(m.key, m.size, m.dateModified, it)) }
                val twin = bySha[h]
                if (twin != null) {
                    dao.insertAll(
                        listOf(
                            BackupEntry(
                                uri = m.key, name = m.name, mime = m.mime, size = m.size, dateTaken = m.dateTaken,
                                isVideo = m.isVideo, sha256 = h, state = BackupEntry.ST_DUPLICATE,
                                chatId = twin.chatId, messageId = twin.messageId, duplicateOf = twin.uri,
                                verifiedAt = now,
                            )
                        )
                    )
                    matched++
                }
            } catch (_: Exception) {
                // unreadable file: skip
            }
            onProgress?.invoke(i + 1, candidates.size)
        }
        matched
    }
}
