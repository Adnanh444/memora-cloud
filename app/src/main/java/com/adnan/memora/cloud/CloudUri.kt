package com.adnan.memora.cloud

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import com.adnan.memora.telegram.TelegramClient
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File

/** Cloud items are addressed as memora-cloud://thumb/<chatId>/<messageId> (small preview) or .../full/... (original, temporary). */
object CloudUri {
    const val SCHEME = "memora-cloud"
    fun thumb(chatId: Long, messageId: Long): Uri = Uri.parse("$SCHEME://thumb/$chatId/$messageId")
    fun full(u: Uri): Uri = Uri.parse("$SCHEME://full/${u.pathSegments.getOrNull(0)}/${u.pathSegments.getOrNull(1)}")
    fun isCloud(u: Uri): Boolean = u.scheme == SCHEME
    fun ids(u: Uri): Pair<Long, Long>? {
        val s = u.pathSegments
        if (s.size < 2) return null
        val c = s[0].toLongOrNull() ?: return null
        val m = s[1].toLongOrNull() ?: return null
        return c to m
    }
}

/** Lets Coil show cloud photos/videos by fetching them from Telegram into a temporary, app-private cache. */
class CloudFetcher(private val app: Context, private val data: Uri) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val ids = CloudUri.ids(data) ?: return null
        val tg = TelegramClient.get(app)
        val path = if (data.host == "full") tg.cloudFullPath(ids.first, ids.second)
        else tg.cloudThumbPath(ids.first, ids.second)
        if (path == null) return null
        return SourceResult(
            source = ImageSource(File(path).toOkioPath(), FileSystem.SYSTEM),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val app: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == CloudUri.SCHEME) CloudFetcher(app, data) else null
    }
}
