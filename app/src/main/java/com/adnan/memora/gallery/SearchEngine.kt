package com.adnan.memora.gallery

import java.util.Calendar
import java.util.Locale

/** Offline keyword search over name, folder, type and date. (Smart AI search arrives with the on-device AI phase.) */
object SearchEngine {
    private val months = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )
    private val photoWords = setOf("photo", "photos", "image", "images", "picture", "pictures")
    private val videoWords = setOf("video", "videos", "clip", "clips")

    private fun dayStart(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun search(all: List<LocalMedia>, query: String): List<LocalMedia> {
        val tokens = query.lowercase(Locale.getDefault()).split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return emptyList()
        val today = dayStart(System.currentTimeMillis())
        val yesterday = dayStart(today - 1)
        val cal = Calendar.getInstance()
        return all.filter { m ->
            cal.timeInMillis = m.dateTaken
            val year = cal.get(Calendar.YEAR).toString()
            val month = months[cal.get(Calendar.MONTH)]
            val day = dayStart(m.dateTaken)
            val name = m.name.lowercase(Locale.getDefault())
            val bucket = m.bucket.lowercase(Locale.getDefault())
            tokens.all { t ->
                name.contains(t) || bucket.contains(t) || year == t ||
                    (t.length >= 3 && month.startsWith(t)) ||
                    (t in videoWords && m.isVideo) ||
                    (t in photoWords && !m.isVideo) ||
                    (t == "today" && day == today) ||
                    (t == "yesterday" && day == yesterday)
            }
        }
    }
}
