package com.adnan.memora.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.adnan.memora.R

/** Uploads the queue in the background, with a notification so Android keeps it alive. */
class UploadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val engine = BackupEngine.get(applicationContext)
        try { setForeground(info(null)) } catch (_: Exception) { /* Android may refuse from the background: still runs, but for a shorter time */ }
        val retry = engine.runQueue { name ->
            try { setForeground(info(name)) } catch (_: Exception) {}
        }
        return if (retry) Result.retry() else Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = info(null)

    private fun info(name: String?): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel("backup", ctx.getString(R.string.backup_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        val s = BackupEngine.get(ctx).summary.value
        val text = if (name != null) "$name  (${s.done}/${s.total})" else ctx.getString(R.string.backup_notif_text)
        val n = NotificationCompat.Builder(ctx, "backup")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(ctx.getString(R.string.backup_notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(s.total.coerceAtLeast(1), s.done, s.total == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(1001, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(1001, n)
    }
}
