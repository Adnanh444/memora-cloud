package com.adnan.memora.org

import android.content.Context
import com.adnan.memora.telegram.SecureKeys
import com.adnan.memora.telegram.TelegramClient
import com.adnan.memora.telegram.UploadResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keeps favorites, archive and albums safe in the cloud storage as ONE ENCRYPTED file (AES-256-GCM),
 * with a second copy in the user's Saved Messages. After a restore it is read back and merged.
 * The key is kept encrypted on the phone and also noted in the user's own Saved Messages so recovery needs no extra password.
 */
object MetaSync {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pending: Job? = null
    private fun prefs(c: Context) = c.getSharedPreferences("memora_meta", Context.MODE_PRIVATE)

    /** Called after every change; waits a few seconds so many quick changes become one upload. */
    fun schedule(ctx: Context) {
        val app = ctx.applicationContext
        pending?.cancel()
        pending = scope.launch {
            delay(8_000)
            try { sync(app) } catch (_: Exception) {}
        }
    }

    private suspend fun export(ctx: Context): ByteArray {
        val d = OrgDb.get(ctx).dao()
        val o = JSONObject()
        o.put("v", 1)
        o.put("fav", JSONArray(d.allFav().map { it.sha }))
        o.put("arch", JSONArray(d.allArch().map { it.sha }))
        val items = d.allAlbumItems().groupBy { it.albumId }
        val albums = JSONArray()
        for (a in d.allAlbums()) {
            val ao = JSONObject()
            ao.put("id", a.id)
            ao.put("name", a.name)
            ao.put("created", a.createdAt)
            ao.put("items", JSONArray((items[a.id] ?: emptyList()).map { it.sha }))
            albums.put(ao)
        }
        o.put("albums", albums)
        return o.toString().toByteArray(Charsets.UTF_8)
    }

    private fun encrypt(key: ByteArray, plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
        return iv + c.doFinal(plain)
    }

    private fun decrypt(key: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, data.copyOfRange(0, 12)))
        return c.doFinal(data.copyOfRange(12, data.size))
    }

    suspend fun sync(ctx: Context): Boolean {
        val tg = TelegramClient.get(ctx)
        if (!tg.awaitUsable(30_000)) return false
        val storage = tg.storage.value ?: return false
        val key = SecureKeys.metaKey(ctx)
        val dir = File(ctx.cacheDir, "upload").apply { mkdirs() }
        val file = File(dir, "memora_meta.bin")
        file.writeBytes(encrypt(key, export(ctx)))
        try {
            tg.ensureKeyNote(storage.storageId, key)
            val p = prefs(ctx)
            val caption = "memora:meta:v1 ts=${System.currentTimeMillis()}"

            val r = tg.uploadDocument(storage.chatId, file.absolutePath, caption, null) { }
            if (r is UploadResult.Success) {
                p.getString("chan_msg", null)?.toLongOrNull()?.let { tg.deleteMessages(storage.chatId, listOf(it)) }
                p.edit().putString("chan_msg", r.messageId.toString()).apply()
            }
            val saved = tg.savedChat()
            if (saved != null) {
                val r2 = tg.uploadDocument(saved, file.absolutePath, caption, null) { }
                if (r2 is UploadResult.Success) {
                    p.getString("saved_msg", null)?.toLongOrNull()?.let { tg.deleteMessages(saved, listOf(it)) }
                    p.edit().putString("saved_msg", r2.messageId.toString()).apply()
                }
            }
            return r is UploadResult.Success
        } finally {
            file.delete()
        }
    }

    /** After a restore: read the newest encrypted file back and merge it into this phone. Returns false if it could not be read. */
    suspend fun restore(ctx: Context, chatId: Long, storageId: String): Boolean {
        val tg = TelegramClient.get(ctx)
        val key = tg.findKeyNote(storageId) ?: return false
        val fileId = tg.findLatestMeta(chatId) ?: return false
        val path = tg.downloadFile(fileId) ?: return false
        val plain = try {
            decrypt(key, File(path).readBytes())
        } catch (_: Exception) {
            tg.deleteFile(fileId)
            return false
        }
        tg.deleteFile(fileId)
        merge(ctx, String(plain, Charsets.UTF_8))
        SecureKeys.setMetaKey(ctx, key)
        return true
    }

    private suspend fun merge(ctx: Context, json: String) {
        val d = OrgDb.get(ctx).dao()
        val o = JSONObject(json)
        val now = System.currentTimeMillis()
        val fav = o.optJSONArray("fav")
        if (fav != null) d.addFav((0 until fav.length()).map { FavEntity(fav.getString(it), now) })
        val arch = o.optJSONArray("arch")
        if (arch != null) d.addArch((0 until arch.length()).map { ArchEntity(arch.getString(it), now) })
        val albums = o.optJSONArray("albums") ?: return
        for (i in 0 until albums.length()) {
            val a = albums.getJSONObject(i)
            val id = a.getString("id")
            d.upsertAlbum(AlbumEntity(id, a.getString("name"), a.optLong("created", now)))
            val items = a.optJSONArray("items") ?: continue
            d.addAlbumItems((0 until items.length()).map { AlbumItemEntity(id, items.getString(it), now) })
        }
    }
}
