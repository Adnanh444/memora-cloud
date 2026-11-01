package com.adnan.memora.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class TypeFilter { ALL, PHOTOS, VIDEOS }

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)
    private val all = MutableStateFlow<List<LocalMedia>>(emptyList())
    private val _loading = MutableStateFlow(false)

    val loading: StateFlow<Boolean> = _loading
    val typeFilter = MutableStateFlow(TypeFilter.ALL)
    val bucket = MutableStateFlow<String?>(null)
    val selection = MutableStateFlow<Set<String>>(emptySet())
    val selectMode = MutableStateFlow(false)

    val buckets: StateFlow<List<String>> = all
        .map { list -> list.groupingBy { it.bucket }.eachCount().entries.sortedByDescending { it.value }.map { it.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visible: StateFlow<List<LocalMedia>> = combine(all, typeFilter, bucket) { list, t, b ->
        list.filter { m ->
            val typeOk = when (t) {
                TypeFilter.ALL -> true
                TypeFilter.PHOTOS -> !m.isVideo
                TypeFilter.VIDEOS -> m.isVideo
            }
            typeOk && (b == null || m.bucket == b)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            all.value = repo.loadAll()
            _loading.value = false
        }
    }

    fun toggle(key: String) {
        selectMode.value = true
        selection.value = selection.value.let { if (key in it) it - key else it + key }
    }

    fun enterSelectMode() { selectMode.value = true }
    fun selectAll() { selectMode.value = true; selection.value = visible.value.map { it.key }.toSet() }
    fun clearSelection() { selection.value = emptySet(); selectMode.value = false }
    fun selectedMedia(): List<LocalMedia> = all.value.filter { it.key in selection.value }
}
