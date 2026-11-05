package com.adnan.memora.data

internal object AppLockPolicy {
    const val GRACE_MS = 30_000L
    fun afterBackground(enabled: Boolean, elapsedMs: Long) = enabled && elapsedMs >= GRACE_MS
}
