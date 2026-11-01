package com.adnan.memora.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** Backup lifecycle of one media item. Only VERIFIED may be treated as safely backed up. */
enum class BackupState { NOT_BACKED_UP, QUEUED, UPLOADING, VERIFIED, FAILED, CLOUD_ONLY, DOWNLOADING, DOWNLOADED }

@Entity(tableName = "media_items", indices = [Index("sha256"), Index("dateTaken")])
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
)

@Dao
interface MediaItemDao {
    @Query("SELECT * FROM media_items ORDER BY dateTaken DESC")
    fun observeByDateTaken(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE sha256 = :hash LIMIT 1")
    suspend fun findByHash(hash: String): MediaItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MediaItemEntity): Long
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
