package com.adnan.memora.telegram

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Protects the key that encrypts TDLib's local database (which holds the Telegram session).
 * A random 256-bit database key is encrypted with a hardware-backed Android Keystore key and stored encrypted.
 */
object SecureKeys {
    private const val ALIAS = "memora_master_key"
    private const val PREFS = "memora_secure"
    private const val ENTRY = "tdlib_db_key"

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, masterKey())
        return Base64.encodeToString(c.iv + c.doFinal(plain), Base64.NO_WRAP)
    }

    private fun decrypt(b64: String): ByteArray {
        val raw = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, raw.copyOfRange(0, 12)))
        return c.doFinal(raw.copyOfRange(12, raw.size))
    }

    /** The user's own Telegram API id/hash, stored encrypted on this phone only. */
    fun saveApiCredentials(ctx: Context, id: Int, hash: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("api_cred", encrypt("$id:$hash".toByteArray())).apply()
    }

    fun loadApiCredentials(ctx: Context): Pair<Int, String>? {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("api_cred", null) ?: return null
        return try {
            val t = String(decrypt(s))
            val i = t.indexOf(':')
            Pair(t.substring(0, i).toInt(), t.substring(i + 1))
        } catch (_: Exception) { null }
    }

    fun clearApiCredentials(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove("api_cred").apply()
    }

    fun databaseKey(ctx: Context): ByteArray {
        val sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = sp.getString(ENTRY, null)
        if (stored != null) {
            try {
                val raw = Base64.decode(stored, Base64.NO_WRAP)
                val iv = raw.copyOfRange(0, 12)
                val ct = raw.copyOfRange(12, raw.size)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
                return c.doFinal(ct)
            } catch (_: Exception) {
                // Keystore key was lost (e.g. after a device restore). The old encrypted TDLib data can no
                // longer be read, so remove it; the user simply signs in to Telegram again.
                File(ctx.filesDir, "tdlib").deleteRecursively()
                File(ctx.filesDir, "tdlib_files").deleteRecursively()
            }
        }
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, masterKey())
        val ct = c.doFinal(key)
        sp.edit().putString(ENTRY, Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)).apply()
        return key
    }
}
