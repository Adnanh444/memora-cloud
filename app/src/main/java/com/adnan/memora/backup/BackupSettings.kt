package com.adnan.memora.backup

import android.content.Context

data class BackupConfig(
    val enabled: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val photos: Boolean = true,
    val videos: Boolean = true,
    val folders: Set<String> = emptySet(),
    val paused: Boolean = false,
)

object BackupSettings {
    private fun sp(c: Context) = c.getSharedPreferences("memora_backup_prefs", Context.MODE_PRIVATE)

    fun load(c: Context): BackupConfig {
        val s = sp(c)
        return BackupConfig(
            enabled = s.getBoolean("enabled", false),
            wifiOnly = s.getBoolean("wifi_only", true),
            chargingOnly = s.getBoolean("charging_only", false),
            photos = s.getBoolean("photos", true),
            videos = s.getBoolean("videos", true),
            folders = (s.getStringSet("folders", emptySet()) ?: emptySet()).toSet(),
            paused = s.getBoolean("paused", false),
        )
    }

    fun save(c: Context, cfg: BackupConfig) {
        sp(c).edit()
            .putBoolean("enabled", cfg.enabled).putBoolean("wifi_only", cfg.wifiOnly)
            .putBoolean("charging_only", cfg.chargingOnly).putBoolean("photos", cfg.photos)
            .putBoolean("videos", cfg.videos).putStringSet("folders", cfg.folders.toSet())
            .putBoolean("paused", cfg.paused).apply()
    }
}
