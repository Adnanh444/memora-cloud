package com.adnan.memora.gallery

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only access to the phone's photos and videos through MediaStore. */
class MediaRepository(private val ctx: Context) {

    suspend fun loadAll(): List<LocalMedia> = withContext(Dispatchers.IO) {
        (query(false) + query(true)).sortedByDescending { it.dateTaken }
    }

    private fun query(video: Boolean): List<LocalMedia> {
        val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val cols = arrayListOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE,
            "datetaken", MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
            "bucket_display_name",
        )
        if (video) cols.add("duration")
        val out = ArrayList<LocalMedia>()
        try {
            ctx.contentResolver.query(base, cols.toTypedArray(), null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val size = c.long("_size")
                    if (size <= 0) continue
                    val modified = c.long("date_modified") * 1000
                    val taken = c.long("datetaken").let { if (it > 0) it else modified }
                    out.add(
                        LocalMedia(
                            uri = ContentUris.withAppendedId(base, c.long("_id")),
                            name = c.str("_display_name") ?: "",
                            mime = c.str("mime_type") ?: if (video) "video/*" else "image/*",
                            size = size,
                            dateTaken = taken,
                            dateModified = modified,
                            width = c.long("width").toInt(),
                            height = c.long("height").toInt(),
                            durationMs = if (video) c.long("duration") else 0L,
                            isVideo = video,
                            bucket = c.str("bucket_display_name") ?: "Other",
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            // permission missing or revoked: return what we have
        }
        return out
    }

    private fun Cursor.long(col: String): Long {
        val i = getColumnIndex(col)
        return if (i >= 0 && !isNull(i)) getLong(i) else 0L
    }

    private fun Cursor.str(col: String): String? {
        val i = getColumnIndex(col)
        return if (i >= 0 && !isNull(i)) getString(i) else null
    }
}
