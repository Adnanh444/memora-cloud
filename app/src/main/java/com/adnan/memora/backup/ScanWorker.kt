package com.adnan.memora.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Runs every ~30 minutes: finds new photos/videos in the auto-backup folders and queues them. */
class ScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val engine = BackupEngine.get(applicationContext)
        return try {
            engine.scanFolders()
            engine.kick()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
