package com.adnan.memora.org

import android.content.Context
import com.adnan.memora.backup.BackupDb
import com.adnan.memora.backup.Hashing
import com.adnan.memora.backup.HashCacheEntry
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.gallery.LocalMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Favorites, archive and albums. Items are identified by their SHA-256, so the same photo is recognised on any phone. */
object OrgRepo {
    /** SHA-256 of each item (computed once and cached if it is not known yet). */
    suspend fun shasFor(ctx: Context, items: List<LocalMedia>, known: Map<String, String>): List<String> =
        withContext(Dispatchers.IO) {
            val dao = BackupDb.get(ctx).dao()
            items.mapNotNull { m ->
                known[m.key] ?: if (CloudUri.isCloud(m.uri)) null else try {
                    val h = Hashing.hashOnly(ctx, m.uri)
                    dao.putHash(HashCacheEntry(m.key, m.size, m.dateModified, h))
                    h
                } catch (_: Exception) { null }
            }.distinct()
        }

    suspend fun favorite(ctx: Context, shas: List<String>, on: Boolean) = withContext(Dispatchers.IO) {
        val d = OrgDb.get(ctx).dao()
        val now = System.currentTimeMillis()
        if (on) d.addFav(shas.map { FavEntity(it, now) }) else shas.chunked(500).forEach { d.removeFav(it) }
        MetaSync.schedule(ctx)
    }

    suspend fun archive(ctx: Context, shas: List<String>, on: Boolean) = withContext(Dispatchers.IO) {
        val d = OrgDb.get(ctx).dao()
        val now = System.currentTimeMillis()
        if (on) d.addArch(shas.map { ArchEntity(it, now) }) else shas.chunked(500).forEach { d.removeArch(it) }
        MetaSync.schedule(ctx)
    }

    suspend fun createAlbum(ctx: Context, name: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        OrgDb.get(ctx).dao().upsertAlbum(AlbumEntity(id, name.trim().ifBlank { "Album" }, System.currentTimeMillis()))
        MetaSync.schedule(ctx)
        id
    }

    suspend fun renameAlbum(ctx: Context, id: String, name: String) = withContext(Dispatchers.IO) {
        val d = OrgDb.get(ctx).dao()
        val old = d.allAlbums().firstOrNull { it.id == id } ?: return@withContext
        d.upsertAlbum(old.copy(name = name.trim().ifBlank { old.name }))
        MetaSync.schedule(ctx)
    }

    /** Deleting an album never deletes its photos or videos. */
    suspend fun deleteAlbum(ctx: Context, id: String) = withContext(Dispatchers.IO) {
        val d = OrgDb.get(ctx).dao()
        d.clearAlbum(id)
        d.deleteAlbum(id)
        MetaSync.schedule(ctx)
    }

    suspend fun addToAlbum(ctx: Context, id: String, shas: List<String>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        OrgDb.get(ctx).dao().addAlbumItems(shas.map { AlbumItemEntity(id, it, now) })
        MetaSync.schedule(ctx)
    }

    suspend fun removeFromAlbum(ctx: Context, id: String, shas: List<String>) = withContext(Dispatchers.IO) {
        val d = OrgDb.get(ctx).dao()
        shas.chunked(500).forEach { d.removeAlbumItems(id, it) }
        MetaSync.schedule(ctx)
    }
}
