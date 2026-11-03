package com.adnan.memora.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the "Pause" button in the backup notification. */
class BackupActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_PAUSE) {
            BackupEngine.init(context)
            BackupEngine.pause(context)
        }
    }

    companion object { const val ACTION_PAUSE = "com.adnan.memora.action.PAUSE_BACKUP" }
}
