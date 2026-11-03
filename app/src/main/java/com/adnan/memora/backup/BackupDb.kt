package com.adnan.memora.backup

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** One row per photo/video that Memora has been asked to back up. This is the persistent backup ledger. */
@Entity(tableName = "backup_entries", indices = [Index("sha256"), Index("state")])
data class BackupEntry(
    @PrimaryKey val uri: String,
    val name: String,
    val mime: String,
    val size: Long,
    val dateTaken: Long,
    val isVideo: Boolean,
    val sha256: String? = null,
    val state: String = ST_QUEUED,
    val chatId: Long = 0,
    val messageId: Long = 0,
    val queuedAt: Long = System.currentTimeMillis(),
    val uploadedAt: Long = 0,
    val verifiedAt: Long = 0,
    val attempts: Int = 0,
    val error: String? = null,
    val duplicateOf: String? = null,
) {
    companion object {
        const val ST_QUEUED = "QUEUED"
        const val ST_UPLOADING = "UPLOADING"
        const val ST_VERIFIED = "VERIFIED"
        const val ST_DUPLICATE = "DUPLICATE"
        const val ST_FAILED = "FAILED"
    }
}

/** Remembers file hashes so unchanged files are never hashed twice. */
@Entity(tableName = "hash_cache")
data class HashCacheEntry(@PrimaryKey val uri: String, val size: Long, val modified: Long, val sha256: String)

@Dao
interface BackupDao {
    @Query("SELECT * FROM backup_entries")
    fun observeAll(): Flow<List<BackupEntry>>

    @Query("SELECT uri FROM backup_entries")
    suspend fun allKeys(): List<String>

    @Query("SELECT * FROM backup_entries WHERE uri = :uri")
    suspend fun get(uri: String): BackupEntry?

    @Query("SELECT uri FROM backup_entries WHERE state = 'QUEUED' OR (state = 'FAILED' AND attempts < :maxAttempts) ORDER BY CASE state WHEN 'QUEUED' THEN 0 ELSE 1 END, dateTaken DESC")
    suspend fun pendingUris(maxAttempts: Int): List<String>

    @Query("SELECT * FROM backup_entries WHERE sha256 = :sha AND state = 'VERIFIED' AND uri != :uri LIMIT 1")
    suspend fun findVerifiedByHash(sha: String, uri: String): BackupEntry?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<BackupEntry>)

    @Update
    suspend fun update(e: BackupEntry)

    @Query("DELETE FROM backup_entries WHERE uri = :uri")
    suspend fun delete(uri: String)

    @Query("UPDATE backup_entries SET state = 'QUEUED' WHERE state = 'UPLOADING'")
    suspend fun resetUploading()

    @Query("UPDATE backup_entries SET state = 'QUEUED', attempts = 0, error = NULL WHERE state = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("UPDATE backup_entries SET state = 'QUEUED', attempts = 0, error = NULL WHERE state = 'FAILED' AND uri IN (:uris)")
    suspend fun requeue(uris: List<String>)

    @Query("SELECT * FROM hash_cache WHERE uri = :uri")
    suspend fun cachedHash(uri: String): HashCacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putHash(h: HashCacheEntry)
}

@Database(entities = [BackupEntry::class, HashCacheEntry::class], version = 1, exportSchema = false)
abstract class BackupDb : RoomDatabase() {
    abstract fun dao(): BackupDao

    companion object {
        @Volatile private var inst: BackupDb? = null
        fun get(ctx: Context): BackupDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, BackupDb::class.java, "memora_backup.db")
                .build().also { inst = it }
        }
    }
}
