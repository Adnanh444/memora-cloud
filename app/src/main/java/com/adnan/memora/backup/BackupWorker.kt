package com.adnan.memora.backup

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.adnan.memora.R
import com.adnan.memora.gallery.BackupPhase
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val CHANNEL_ID = "memora_backup"
private const val NOTIF_ID = 4101

private fun ensureChannel(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 26) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.bk_notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }
}

private fun buildNotification(ctx: Context, done: Int, total: Int, name: String?): Notification {
    val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle(ctx.getString(R.string.bk_notif_title))
        .setContentText(if (total > 0) ctx.getString(R.string.bk_notif_text, done, total) + (if (name != null) " · $name" else "") else "")
        .setOngoing(true)
        .setOnlyAlertOnce(true)
    if (total > 0) b.setProgress(total, done, false) else b.setProgress(0, 0, true)
    val pause = PendingIntent.getBroadcast(
        ctx, 0, Intent(ctx, BackupActionReceiver::class.java).setAction(BackupActionReceiver.ACTION_PAUSE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    b.addAction(android.R.drawable.ic_media_pause, ctx.getString(R.string.bk_pause), pause)
    return b.build()
}

/** Scans for new media (if automatic backup is on) and uploads everything waiting in the queue. */
class BackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        BackupEngine.init(ctx)
        if (BackupSettings.load(ctx).paused) return Result.success()
        ensureChannel(ctx)
        BackupEngine.scanAndEnqueue(ctx)
        try {
            val info = if (Build.VERSION.SDK_INT >= 29)
                ForegroundInfo(NOTIF_ID, buildNotification(ctx, 0, 0, null), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(NOTIF_ID, buildNotification(ctx, 0, 0, null))
            setForeground(info)
        } catch (_: Throwable) {
            // Android may refuse a foreground service from the background; the work still runs.
        }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return coroutineScope {
            val watcher = launch {
                BackupEngine.summary.collect { s ->
                    if (s.phase == BackupPhase.RUNNING) {
                        try { nm.notify(NOTIF_ID, buildNotification(ctx, s.done, s.total, s.currentName)) } catch (_: Throwable) {}
                    }
                }
            }
            val usable = BackupEngine.runUploads(ctx)
            watcher.cancel()
            if (usable) Result.success() else Result.retry()
        }
    }
}

/** Fires when new photos/videos appear, then re-arms itself. */
class TriggerWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val cfg = BackupSettings.load(applicationContext)
        if (cfg.enabled && !cfg.paused) BackupScheduler.runNow(applicationContext)
        if (cfg.enabled) BackupScheduler.armTrigger(applicationContext)
        return Result.success()
    }
}

object BackupScheduler {
    private const val NOW = "memora_backup_now"
    private const val PERIODIC = "memora_backup_periodic"
    private const val TRIGGER = "memora_backup_trigger"

    private fun constraints(cfg: BackupConfig): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (cfg.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(cfg.chargingOnly)
        .build()

    fun runNow(ctx: Context, append: Boolean = false) {
        val cfg = BackupSettings.load(ctx)
        val req = OneTimeWorkRequestBuilder<BackupWorker>().setConstraints(constraints(cfg)).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(
            NOW, if (append) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.KEEP, req,
        )
    }

    fun armTrigger(ctx: Context) {
        val c = Constraints.Builder()
            .addContentUriTrigger(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true)
            .addContentUriTrigger(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true)
            .setTriggerContentUpdateDelay(8, TimeUnit.SECONDS)
            .setTriggerContentMaxDelay(60, TimeUnit.SECONDS)
            .build()
        val req = OneTimeWorkRequestBuilder<TriggerWorker>().setConstraints(c).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(TRIGGER, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    /** Applies the current settings: schedules (or cancels) automatic backup. */
    fun sync(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        val cfg = BackupSettings.load(ctx)
        if (cfg.enabled) {
            val periodic = PeriodicWorkRequestBuilder<BackupWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints(cfg)).build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, periodic)
            armTrigger(ctx)
        } else {
            wm.cancelUniqueWork(PERIODIC)
            wm.cancelUniqueWork(TRIGGER)
        }
    }
}
