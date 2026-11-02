package com.adnan.memora.telegram

import android.content.Context
import com.adnan.memora.BuildConfig

object TelegramConfig {
    private val buildHasKey: Boolean
        get() = BuildConfig.TELEGRAM_API_ID > 0 && BuildConfig.TELEGRAM_API_HASH.isNotBlank()

    fun hasUserCredentials(ctx: Context): Boolean = SecureKeys.loadApiCredentials(ctx) != null

    /**
     * Each user uses their OWN Telegram API key (entered in the app, stored encrypted on this phone).
     * A key baked in at build time is only an optional fallback.
     */
    fun credentials(ctx: Context): Pair<Int, String>? =
        SecureKeys.loadApiCredentials(ctx)
            ?: if (buildHasKey) Pair(BuildConfig.TELEGRAM_API_ID, BuildConfig.TELEGRAM_API_HASH) else null
}
