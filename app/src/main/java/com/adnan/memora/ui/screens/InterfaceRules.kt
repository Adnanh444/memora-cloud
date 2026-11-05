package com.adnan.memora.ui.screens

/** Pure presentation rules; no media writes or backend dependencies. */
internal object BinSelection {
    fun retain(selected: Set<String>, visible: Set<String>): Set<String> = selected.intersect(visible)
    fun toggleAll(selected: Set<String>, visible: Set<String>): Set<String> =
        if (visible.isNotEmpty() && selected.containsAll(visible)) emptySet() else visible.toSet()
}
