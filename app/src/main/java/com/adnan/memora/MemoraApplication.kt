package com.adnan.memora

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("memora", Context.MODE_PRIVATE)
    fun themeMode(c: Context): Int = sp(c).getInt("night_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    /** true = clean Google Photos-like look (default); false = neon glow look. */
    fun cleanStyle(c: Context): Boolean = sp(c).getBoolean("clean_style", true)
    fun setCleanStyle(c: Context, clean: Boolean) { sp(c).edit().putBoolean("clean_style", clean).apply() }
    fun setThemeMode(c: Context, mode: Int) {
        sp(c).edit().putInt("night_mode", mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
    }
}

class MemoraApplication : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        AppCompatDelegate.setDefaultNightMode(Prefs.themeMode(this))
        // Start Telegram only if this build has an API key; restores a previous login automatically.
        com.adnan.memora.telegram.TelegramClient.get(this).start()
        com.adnan.memora.backup.BackupEngine.init(this)
        com.adnan.memora.backup.BackupScheduler.sync(this)
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(VideoFrameDecoder.Factory())
            add(com.adnan.memora.cloud.CloudFetcher.Factory(this@MemoraApplication))
        }
        .crossfade(true)
        .build()
}
