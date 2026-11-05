package com.adnan.memora.backup

import java.io.Closeable
import java.io.File
import java.io.IOException

/** A private attempt directory keeps identical source names and thumbnails from colliding. */
internal class UploadWorkspace private constructor(val directory: File, val original: File) : Closeable {
    val thumbnail = File(directory, "preview.jpg")

    override fun close() { directory.deleteRecursively() }

    companion object {
        fun create(root: File, sourceName: String): UploadWorkspace {
            if (!root.isDirectory && !root.mkdirs()) throw IOException("Cannot create upload cache")
            val directory = File.createTempFile("attempt-", ".tmp", root)
            if (!directory.delete() || !directory.mkdir()) throw IOException("Cannot create upload attempt")
            try {
                val originals = File(directory, "original")
                if (!originals.mkdir()) throw IOException("Cannot create original directory")
                val name = sourceName.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
                    .trim().take(120).takeUnless { it.isBlank() || it == "." || it == ".." } ?: "file"
                return UploadWorkspace(directory, File(originals, name))
            } catch (e: Exception) {
                directory.deleteRecursively()
                throw e
            }
        }
    }
}
