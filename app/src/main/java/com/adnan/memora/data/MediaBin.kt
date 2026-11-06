package com.adnan.memora.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import com.adnan.memora.gallery.LocalMedia
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Memora's own bin.
 *
 * Delete never asks Android for permission and never erases a file on the spot:
 * the bytes are copied into the app's private bin folder, the original is removed
 * from the gallery, and the copy stays restorable for [RETENTION_DAYS] days.
 *
 * A silent delete needs "All files access" (MANAGE_EXTERNAL_STORAGE): without it
 * Android refuses to let any app remove a photo it did not create, and the only
 * alternative is the system Allow/Deny dialog. [enabled] answers that question,
 * [settingsIntent] opens the one system screen that grants it.
 */
object MediaBin {

    const val RETENTION_DAYS = 30
    private const val INDEX = "index.json"

    data class Item(
        val id: String,
        val name: String,
        val mime: String,
        val isVideo: Boolean,
        val size: Long,
        val deletedAt: Long,
        val originalPath: String,
        val originalUri: String,
        val bucket: String,
    ) {
        val daysLeft: Int
            get() {
                val ageDays = ((System.currentTimeMillis() - deletedAt) / 86_400_000L).toInt()
                return (RETENTION_DAYS - ageDays).coerceAtLeast(0)
            }
    }

    fun dir(ctx: Context): File = File(ctx.filesDir, "bin").apply { mkdirs() }

    private fun indexPath(ctx: Context) = File(dir(ctx), INDEX)

    /** True when a delete can happen without any Android dialog. */
    fun enabled(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else true

    /** The single system screen where "All files access" is switched on. */
    fun settingsIntent(ctx: Context): Intent {
        val perApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${ctx.packageName}"),
        )
        return if (Build.VERSION.SDK_INT >= 30 && perApp.resolveActivity(ctx.packageManager) != null) perApp
        else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    @Synchronized
    fun items(ctx: Context): List<Item> {
        val file = indexPath(ctx)
        if (!file.isFile) return emptyList()
        val out = ArrayList<Item>()
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                val item = Item(
                    id = o.optString("id"),
                    name = o.optString("name"),
                    mime = o.optString("mime"),
                    isVideo = o.optBoolean("isVideo"),
                    size = o.optLong("size"),
                    deletedAt = o.optLong("deletedAt"),
                    originalPath = o.optString("originalPath"),
                    originalUri = o.optString("originalUri"),
                    bucket = o.optString("bucket"),
                )
                if (File(dir(ctx), item.id).isFile) out += item
            }
        }
        out.sortByDescending { it.deletedAt }
        return out
    }

    @Synchronized
    private fun save(ctx: Context, list: List<Item>) {
        val array = JSONArray()
        list.forEach { e ->
            array.put(
                JSONObject().apply {
                    put("id", e.id); put("name", e.name); put("mime", e.mime); put("isVideo", e.isVideo)
                    put("size", e.size); put("deletedAt", e.deletedAt); put("originalPath", e.originalPath)
                    put("originalUri", e.originalUri); put("bucket", e.bucket)
                }
            )
        }
        indexPath(ctx).writeText(array.toString())
    }

    fun totalSize(ctx: Context): Long = items(ctx).sumOf { it.size }

    /** Real file path behind a MediaStore uri, or null when it cannot be resolved. */
    private fun pathOf(ctx: Context, uri: Uri): String? = runCatching {
        ctx.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /**
     * Moves [list] into the bin. Returns how many files moved; files that could not be
     * read or deleted are left untouched and simply not counted.
     */
    fun moveToBin(ctx: Context, list: List<LocalMedia>): Int {
        if (list.isEmpty()) return 0
        var moved = 0
        val kept = items(ctx).toMutableList()
        for (media in list) {
            val file = pathOf(ctx, media.uri)?.let(::File)
            if (file == null || !file.isFile) continue
            val id = "b_${System.currentTimeMillis()}_${(1000..9999).random()}"
            val target = File(dir(ctx), id)
            val copied = runCatching {
                ctx.contentResolver.openInputStream(media.uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                } != null && target.length() > 0
            }.getOrDefault(false)
            if (!copied) { target.delete(); continue }
            val removed = file.delete()
            if (!removed) { target.delete(); continue }   // keep the original, forget the copy
            // the file is gone; let the gallery notice even if the row is stale
            runCatching { ctx.contentResolver.delete(media.uri, null, null) }
            kept += Item(
                id = id, name = media.name, mime = media.mime, isVideo = media.isVideo,
                size = target.length(), deletedAt = System.currentTimeMillis(),
                originalPath = file.absolutePath, originalUri = media.uri.toString(), bucket = media.bucket,
            )
            moved++
        }
        save(ctx, kept)
        return moved
    }

    /** Puts one bin item back at its original path. */
    fun restore(ctx: Context, item: Item): Boolean {
        val source = File(dir(ctx), item.id)
        if (!source.isFile) return false
        val target = File(item.originalPath)
        val ok = runCatching {
            target.parentFile?.mkdirs()
            source.inputStream().use { input -> target.outputStream().use { out -> input.copyTo(out) } }
            target.length() == source.length()
        }.getOrDefault(false)
        if (!ok) return false
        source.delete()
        save(ctx, items(ctx).filterNot { it.id == item.id })
        return true
    }

    fun deleteForever(ctx: Context, item: Item) {
        File(dir(ctx), item.id).delete()
        save(ctx, items(ctx).filterNot { it.id == item.id })
    }

    fun empty(ctx: Context) {
        dir(ctx).listFiles()?.forEach { if (it.name != INDEX) it.delete() }
        save(ctx, emptyList())
    }

    /** Drops entries older than the retention window. Safe to call on every start. */
    fun purge(ctx: Context) {
        val list = items(ctx)
        if (list.isEmpty()) return
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 86_400_000L
        val expired = list.filter { it.deletedAt < cutoff }
        if (expired.isEmpty()) return
        expired.forEach { File(dir(ctx), it.id).delete() }
        save(ctx, list.filterNot { it.deletedAt < cutoff })
    }

    fun uriOf(ctx: Context, item: Item): Uri =
        runCatching { ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), item.id.hashCode().toLong()) }
            .getOrElse { Uri.fromFile(File(dir(ctx), item.id)) }
}
