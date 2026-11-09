package com.adnan.memora.data

import android.content.Context

/**
 * Folders that exist only in the cloud — the ones the owner creates by hand, e.g. "Travel 2024".
 * Phone folders do not need to be listed here: they are read from each file's own `dir`.
 */
object FolderPrefs {
    private const val FILE = "memora_folders"
    private const val KEY = "manual"

    fun manual(ctx: Context): List<String> =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY, emptySet())
            ?.filter { it.isNotBlank() }?.sorted() ?: emptyList()

    fun add(ctx: Context, name: String) {
        val clean = name.trim().replace('/', '-')
        if (clean.isBlank()) return
        val now = manual(ctx).toMutableSet()
        now.add(clean)
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(KEY, now).apply()
    }

    fun remove(ctx: Context, name: String) {
        val now = manual(ctx).toMutableSet()
        now.remove(name)
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(KEY, now).apply()
    }
}
