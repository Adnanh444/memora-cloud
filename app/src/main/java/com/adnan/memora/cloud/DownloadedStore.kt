package com.adnan.memora.cloud

import android.content.Context

/** Cloud files that were downloaded to this phone. Keyed by chatId:messageId. */
object DownloadedStore {
    private const val FILE = "memora_downloads"
    private const val KEY = "done"

    fun keys(ctx: Context): Set<String> =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet(KEY, emptySet()) ?: emptySet()

    fun mark(ctx: Context, key: String) {
        val now = keys(ctx).toMutableSet()
        if (now.add(key)) ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet(KEY, now).apply()
    }
}
