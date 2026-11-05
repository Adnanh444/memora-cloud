package com.adnan.memora.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Connection and storage policy for cloud media.
 *
 * The phone gallery is always read from the device, so local thumbnails never cost data.
 * Only Telegram (cloud) previews are gated here: on a metered connection nothing is fetched
 * unless the user explicitly allows it, so scrolling the cloud grid on mobile data is free.
 */
object NetworkPolicy {

    private fun sp(c: Context) = c.getSharedPreferences("memora", Context.MODE_PRIVATE)

    /** Default false: cloud previews are not downloaded while on mobile data. */
    fun cloudOnMetered(c: Context): Boolean = sp(c).getBoolean("cloud_on_metered", false)
    fun setCloudOnMetered(c: Context, allow: Boolean) =
        sp(c).edit().putBoolean("cloud_on_metered", allow).apply()

    /** Ceiling in MB for on-disk preview/thumbnail caches. 200 / 500 / 1000 are offered in Settings. */
    fun cacheCapMb(c: Context): Int = sp(c).getInt("cache_cap_mb", 500)
    fun setCacheCapMb(c: Context, mb: Int) = sp(c).edit().putInt("cache_cap_mb", mb.coerceIn(200, 1000)).apply()

    /** Disk bytes for the image cache: half of the ceiling, so Telegram's own media cache keeps room. */
    fun imageDiskBytes(c: Context): Long = cacheCapMb(c).coerceIn(200, 1000) * 1024L * 1024L / 2

    fun isMetered(c: Context): Boolean {
        val cm = c.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val net = cm.activeNetwork ?: return false // offline: nothing can be fetched anyway
        val caps = cm.getNetworkCapabilities(net) ?: return true
        if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * True when a cloud preview may be fetched right now.
     * Unmetered (Wi-Fi/ethernet) always allows; metered follows the user's switch.
     */
    fun allowsCloudFetch(c: Context): Boolean = !isMetered(c) || cloudOnMetered(c)

    /** Deletes the app's preview/thumbnail disk cache. Phone originals and cloud files are untouched. */
    fun clearImageCache(c: Context): Long {
        val dir = java.io.File(c.cacheDir, "image_cache")
        val before = dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
        dir.deleteRecursively()
        dir.mkdirs()
        return before
    }

    fun imageCacheBytes(c: Context): Long {
        val dir = java.io.File(c.cacheDir, "image_cache")
        return dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }
}
