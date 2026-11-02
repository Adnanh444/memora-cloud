package com.adnan.memora.telegram

import android.content.Context

/** The Telegram channel Memora uses for this phone. Contains no secrets, only identifiers. */
data class StorageConfig(val chatId: Long, val storageId: String, val title: String, val createdAt: Long)

object StorageStore {
    private fun sp(c: Context) = c.getSharedPreferences("memora_storage", Context.MODE_PRIVATE)

    fun load(c: Context): StorageConfig? {
        val s = sp(c)
        val id = s.getLong("chat_id", 0L)
        val sid = s.getString("storage_id", null)
        if (id == 0L || sid == null) return null
        return StorageConfig(id, sid, s.getString("title", "") ?: "", s.getLong("created", 0L))
    }

    fun save(c: Context, cfg: StorageConfig) {
        sp(c).edit().putLong("chat_id", cfg.chatId).putString("storage_id", cfg.storageId)
            .putString("title", cfg.title).putLong("created", cfg.createdAt).apply()
    }

    /** Forgets the storage on this phone only. Nothing in Telegram is touched. */
    fun clear(c: Context) { sp(c).edit().clear().apply() }
}
