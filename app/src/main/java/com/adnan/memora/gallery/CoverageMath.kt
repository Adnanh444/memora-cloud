package com.adnan.memora.gallery

data class CoverageItem(val key: String, val video: Boolean)
data class CoverageCount(val total: Int, val verified: Int) {
    val fraction: Float get() = if (total <= 0) 0f else verified.coerceIn(0, total).toFloat() / total
}
data class MediaCoverage(val photos: CoverageCount, val videos: CoverageCount)
object CoverageMath {
    fun calculate(items: List<CoverageItem>, verifiedKeys: Set<String>): MediaCoverage {
        val unique = items.distinctBy { it.key }
        val photos = unique.filter { !it.video }; val videos = unique.filter { it.video }
        return MediaCoverage(CoverageCount(photos.size, photos.count { it.key in verifiedKeys }),
            CoverageCount(videos.size, videos.count { it.key in verifiedKeys }))
    }
}

internal object CollapseRules {
    fun next(current: Boolean, distance: Float, threshold: Float): Boolean = when {
        distance < -threshold -> true
        distance > threshold -> false
        else -> current
    }
}
