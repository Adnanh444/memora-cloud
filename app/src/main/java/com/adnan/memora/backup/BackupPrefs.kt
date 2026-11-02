package com.adnan.memora.backup

import android.content.Context

/** Backup settings kept on this phone: which folders back up automatically, and Wi-Fi only. */
class BackupPrefs(c: Context) {
    private val sp = c.applicationContext.getSharedPreferences("memora_backup", Context.MODE_PRIVATE)

    fun autoFolders(): Set<String> = sp.getStringSet("auto_folders", emptySet())?.toSet() ?: emptySet()
    fun setAutoFolders(v: Set<String>) { sp.edit().putStringSet("auto_folders", HashSet(v)).apply() }

    /** On by default: a big first backup should not eat mobile data. */
    fun wifiOnly(): Boolean = sp.getBoolean("wifi_only", true)
    fun setWifiOnly(v: Boolean) { sp.edit().putBoolean("wifi_only", v).apply() }
}
