package com.adnan.memora.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest

/** Streaming SHA-256: files are read in small blocks, never loaded into memory. */
object Hashing {
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    suspend fun copyAndHash(ctx: Context, uri: Uri, dest: File): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance("SHA-256")
        val input = ctx.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
        input.use { ins ->
            dest.outputStream().use { out ->
                val buf = ByteArray(256 * 1024)
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    md.update(buf, 0, n)
                    out.write(buf, 0, n)
                    ensureActive()
                }
            }
        }
        hex(md.digest())
    }

    suspend fun hashOnly(ctx: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val md = MessageDigest.getInstance("SHA-256")
        val input = ctx.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
        input.use { ins ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val n = ins.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
                ensureActive()
            }
        }
        hex(md.digest())
    }
}
