package com.adnan.memora.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Backup lifecycle of one media item. Only VERIFIED may be treated as safely backed up. */
enum class BackupState { NOT_BACKED_UP, QUEUED, UPLOADING, VERIFIED, FAILED, CLOUD_ONLY, DOWNLOADING, DOWNLOADED }

@Entity(tableName = "media_items", indices = [Index("sha256"), Index("dateTaken"), Index(value = ["localUri"], unique = true)])
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val localUri: String? = null,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String? = null,
    val dateTaken: Long,            // kept separate from the other dates (spec §9)
    val lastModified: Long,
    val uploadDate: Long? = null,
    val lastDownloaded: Long? = null,
    val telegramChatId: Long? = null,
    val telegramMessageId: Long? = null,
    val verifiedAt: Long? = null,
    val state: BackupState = BackupState.NOT_BACKED_UP,
    val attempts: Int = 0,
    val errorMessage: String? = null,
)

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY dateTaken DESC")
    fun observeByDateTaken(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE sha256 = :hash LIMIT 1")
    suspend fun findByHash(hash: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity): Long

    // ---- Phase 4: backup queue ----
    @Query("SELECT * FROM media_items WHERE localUri = :uri LIMIT 1")
    suspend fun findByUri(uri: String): MediaItemEntity?

    /** Adds a row only if this local file is not known yet. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(item: MediaItemEntity): Long

    @Update
    suspend fun update(item: MediaItemEntity)

    @Query("SELECT * FROM media_items WHERE state = 'QUEUED' ORDER BY dateTaken DESC LIMIT 1")
    suspend fun nextQueued(): MediaItemEntity?

    /** After a crash or a stopped worker nothing is really uploading: put those items back in the queue. */
    @Query("UPDATE media_items SET state = 'QUEUED' WHERE state = 'UPLOADING'")
    suspend fun resetUploading()

    @Query("UPDATE media_items SET state = 'QUEUED', attempts = 0, errorMessage = NULL WHERE state = 'FAILED'")
    suspend fun requeueFailed()

    @Query("SELECT * FROM media_items WHERE localUri IS NOT NULL AND state != 'NOT_BACKED_UP'")
    fun observeBackupRows(): Flow<List<MediaItemEntity>>
}

class Converters {
    @TypeConverter fun fromState(s: BackupState): String = s.name
    @TypeConverter fun toState(s: String): BackupState = BackupState.valueOf(s)
}

// exportSchema stays false until Phase 9, when migrations + schema export are introduced.
@Database(entities = [MediaItemEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class MemoraDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaItemDao
}

/** One shared database for the whole app. */
object MemoraDb {
    @Volatile private var inst: MemoraDatabase? = null
    fun get(ctx: android.content.Context): MemoraDatabase =
        inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, MemoraDatabase::class.java, "memora.db").build().also { inst = it }
        }
}
