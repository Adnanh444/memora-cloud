package com.adnan.memora.gallery

import android.net.Uri

data class LocalMedia(
    val uri: Uri,
    val name: String,
    val mime: String,
    val size: Long,
    val dateTaken: Long,      // when the photo/video was captured
    val dateModified: Long,   // last modified on this device
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val isVideo: Boolean,
    val bucket: String,       // folder name, e.g. Camera, Screenshots
) {
    val key: String get() = uri.toString()
}
