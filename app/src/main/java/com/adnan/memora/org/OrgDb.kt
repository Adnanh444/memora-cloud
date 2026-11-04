package com.adnan.memora.org

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "fav") data class FavEntity(@PrimaryKey val sha: String, val at: Long)
@Entity(tableName = "arch") data class ArchEntity(@PrimaryKey val sha: String, val at: Long)
@Entity(tableName = "albums") data class AlbumEntity(@PrimaryKey val id: String, val name: String, val createdAt: Long)
@Entity(tableName = "album_items", primaryKeys = ["albumId", "sha"])
data class AlbumItemEntity(val albumId: String, val sha: String, val addedAt: Long)

@Dao
interface OrgDao {
    @Query("SELECT sha FROM fav") fun favorites(): Flow<List<String>>
    @Query("SELECT sha FROM arch") fun archived(): Flow<List<String>>
    @Query("SELECT * FROM albums ORDER BY createdAt DESC") fun albums(): Flow<List<AlbumEntity>>
    @Query("SELECT * FROM album_items") fun albumItems(): Flow<List<AlbumItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFav(list: List<FavEntity>)
    @Query("DELETE FROM fav WHERE sha IN (:shas)") suspend fun removeFav(shas: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addArch(list: List<ArchEntity>)
    @Query("DELETE FROM arch WHERE sha IN (:shas)") suspend fun removeArch(shas: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAlbum(a: AlbumEntity)
    @Query("DELETE FROM albums WHERE id = :id") suspend fun deleteAlbum(id: String)
    @Query("DELETE FROM album_items WHERE albumId = :id") suspend fun clearAlbum(id: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addAlbumItems(list: List<AlbumItemEntity>)
    @Query("DELETE FROM album_items WHERE albumId = :id AND sha IN (:shas)") suspend fun removeAlbumItems(id: String, shas: List<String>)

    @Query("SELECT * FROM fav") suspend fun allFav(): List<FavEntity>
    @Query("SELECT * FROM arch") suspend fun allArch(): List<ArchEntity>
    @Query("SELECT * FROM albums") suspend fun allAlbums(): List<AlbumEntity>
    @Query("SELECT * FROM album_items") suspend fun allAlbumItems(): List<AlbumItemEntity>
}

@Database(entities = [FavEntity::class, ArchEntity::class, AlbumEntity::class, AlbumItemEntity::class], version = 1, exportSchema = false)
abstract class OrgDb : RoomDatabase() {
    abstract fun dao(): OrgDao

    companion object {
        @Volatile private var inst: OrgDb? = null
        fun get(ctx: Context): OrgDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, OrgDb::class.java, "memora_org.db").build().also { inst = it }
        }
    }
}
