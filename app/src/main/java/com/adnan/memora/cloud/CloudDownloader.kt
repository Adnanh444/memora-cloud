package com.adnan.memora.cloud

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import com.adnan.memora.R
import com.adnan.memora.backup.BackupEntry
import com.adnan.memora.gallery.LocalMedia
import com.adnan.memora.telegram.TelegramClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class DownloadState(
    val total: Int, val done: Int, val current: String?, val progress: Float,
    val paused: Boolean = false,
)

/**
 * Saves ORIGINAL files from the cloud to the phone gallery (Pictures/Memora or Movies/Memora).
 * This only ever happens when the user taps Download. It never uploads anything.
 */
object CloudDownloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<DownloadState?>(null)
    val state: StateFlow<DownloadState?> = _state

    /** Files waiting to be downloaded, and how far the current run has got. */
    private var queue: List<LocalMedia> = emptyList()
    private var doneCount = 0
    private var paused = false

    /** Finishes the file in flight, then stops. Nothing is left half-written. */
    fun pause() {
        paused = true
        _state.value = _state.value?.copy(paused = true)
    }

    fun resume(ctx: Context) {
        if (job?.isActive == true) { paused = false; _state.value = _state.value?.copy(paused = false); return }
        val rest = queue.drop(doneCount)
        if (rest.isEmpty()) return
        paused = false
        run(ctx.applicationContext, rest, doneCount, queue.size)
    }

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
    }

    fun start(ctx: Context, items: List<LocalMedia>) {
        if (job?.isActive == true || items.isEmpty()) return
        queue = items
        doneCount = 0
        paused = false
        run(ctx.applicationContext, items, 0, items.size)
    }

    private fun run(app: Context, items: List<LocalMedia>, startAt: Int, total: Int) {
        job = scope.launch {
            val tg = TelegramClient.get(app)
            var saved = 0
            _state.value = DownloadState(total, startAt, null, 0f)
            for ((offset, m) in items.withIndex()) {
                val i = startAt + offset
                if (!isActive || paused) break
                val ids = CloudUri.ids(m.uri) ?: continue
                _state.value = DownloadState(total, i, m.name, 0f)
                val path = tg.cloudFullPath(ids.first, ids.second) { p ->
                    _state.value = DownloadState(total, i, m.name, p)
                }
                if (path == null) continue
                try {
                    val uri = saveToGallery(app, File(path), m)
                    if (uri != null) {
                        saved++
                        downloaded(app, m, ids.first, ids.second, uri)
                    }
                } catch (_: Exception) {
                }
                doneCount = i + 1
            }
            tg.releaseFullFiles()
            if (paused) {
                _state.value = DownloadState(total, doneCount, null, 0f, paused = true)
            } else {
                _state.value = null
                queue = emptyList()
                doneCount = 0
                toast(app, app.getString(R.string.cl_saved, saved, total))
            }
        }
    }

    /**
     * Remembers that this cloud file now exists on the phone, and records the downloaded copy as a
     * duplicate of the cloud file. That is what stops the backup from ever uploading it again.
     */
    private suspend fun downloaded(app: Context, m: LocalMedia, chatId: Long, messageId: Long, localUri: android.net.Uri) {
        DownloadedStore.mark(app, "${chatId}:${messageId}")
        try {
            val dao = com.adnan.memora.backup.BackupDb.get(app).dao()
            dao.insertAll(
                listOf(
                    com.adnan.memora.backup.BackupEntry(
                        uri = localUri.toString(), name = m.name, mime = m.mime, size = m.size,
                        dateTaken = m.dateTaken, isVideo = m.isVideo, state = BackupEntry.ST_DUPLICATE,
                        chatId = chatId, messageId = messageId, duplicateOf = "cloud:$chatId:$messageId",
                        verifiedAt = System.currentTimeMillis(), dir = m.bucket,
                    )
                )
            )
        } catch (_: Throwable) {
            // the download itself succeeded; the record is only an optimisation
        }
    }

    fun cancel() { job?.cancel(); paused = false; queue = emptyList(); doneCount = 0; _state.value = null }

    private fun saveToGallery(ctx: Context, src: File, m: LocalMedia): android.net.Uri? {
        val folder = if (m.isVideo) "Movies" else "Pictures"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, m.name)
                put(MediaStore.MediaColumns.MIME_TYPE, m.mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$folder/Memora")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                put("datetaken", m.dateTaken)
            }
            val collection = if (m.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val uri = ctx.contentResolver.insert(collection, values) ?: return null
            val out = ctx.contentResolver.openOutputStream(uri) ?: return null
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
            return uri
        }
        val dirType = if (m.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val dir = File(Environment.getExternalStoragePublicDirectory(dirType), "Memora").apply { mkdirs() }
        var dest = File(dir, m.name)
        var n = 1
        while (dest.exists()) { dest = File(dir, "${n++}_${m.name}") }
        src.copyTo(dest)
        dest.setLastModified(m.dateTaken)
        MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(m.mime), null)
        return android.net.Uri.fromFile(dest)
    }
}
