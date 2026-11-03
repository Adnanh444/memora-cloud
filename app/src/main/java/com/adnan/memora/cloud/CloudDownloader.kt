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

data class DownloadState(val total: Int, val done: Int, val current: String?, val progress: Float)

/**
 * Saves ORIGINAL files from the cloud to the phone gallery (Pictures/Memora or Movies/Memora).
 * This only ever happens when the user taps Download. It never uploads anything.
 */
object CloudDownloader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _state = MutableStateFlow<DownloadState?>(null)
    val state: StateFlow<DownloadState?> = _state

    private fun toast(ctx: Context, text: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(ctx, text, Toast.LENGTH_LONG).show() }
    }

    fun start(ctx: Context, items: List<LocalMedia>) {
        if (job?.isActive == true || items.isEmpty()) return
        val app = ctx.applicationContext
        job = scope.launch {
            val tg = TelegramClient.get(app)
            var saved = 0
            _state.value = DownloadState(items.size, 0, null, 0f)
            for ((i, m) in items.withIndex()) {
                if (!isActive) break
                val ids = CloudUri.ids(m.uri) ?: continue
                _state.value = DownloadState(items.size, i, m.name, 0f)
                val path = tg.cloudFullPath(ids.first, ids.second) { p ->
                    _state.value = DownloadState(items.size, i, m.name, p)
                }
                if (path == null) continue
                try {
                    if (saveToGallery(app, File(path), m)) saved++
                } catch (_: Exception) {
                }
            }
            tg.releaseFullFiles()
            _state.value = null
            toast(app, app.getString(R.string.cl_saved, saved, items.size))
        }
    }

    fun cancel() { job?.cancel(); _state.value = null }

    private fun saveToGallery(ctx: Context, src: File, m: LocalMedia): Boolean {
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
            val uri = ctx.contentResolver.insert(collection, values) ?: return false
            val out = ctx.contentResolver.openOutputStream(uri) ?: return false
            out.use { o -> src.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            ctx.contentResolver.update(uri, values, null, null)
            return true
        }
        val dirType = if (m.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        val dir = File(Environment.getExternalStoragePublicDirectory(dirType), "Memora").apply { mkdirs() }
        var dest = File(dir, m.name)
        var n = 1
        while (dest.exists()) { dest = File(dir, "${n++}_${m.name}") }
        src.copyTo(dest)
        dest.setLastModified(m.dateTaken)
        MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(m.mime), null)
        return true
    }
}
