package com.adnan.memora.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class FolderInfo(val name: String, val count: Int, val cover: LocalMedia, val latest: Long)

class GalleryViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MediaRepository(app)

    private val _all = MutableStateFlow<List<LocalMedia>>(emptyList())
    val all: StateFlow<List<LocalMedia>> = _all
    private val _trash = MutableStateFlow<List<TrashedMedia>>(emptyList())
    val trash: StateFlow<List<TrashedMedia>> = _trash
    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    val selection = MutableStateFlow<Set<String>>(emptySet())
    val selectMode = MutableStateFlow(false)
    val selecting: StateFlow<Boolean> = combine(selectMode, selection) { m, s -> m || s.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Backup UI state. Fed by the real backup engine from Phase 4; until then it says "not set up".
    val backupSummary = MutableStateFlow(BackupSummary())
    val itemBackup = MutableStateFlow<Map<String, ItemBackup>>(emptyMap())

    val folders: StateFlow<List<FolderInfo>> = _all.map { list ->
        list.groupBy { it.bucket }
            .map { (name, l) -> FolderInfo(name, l.size, l.first(), l.maxOf { it.dateTaken }) }
            .sortedWith(
                compareByDescending<FolderInfo> { it.name.equals("Camera", ignoreCase = true) }
                    .thenByDescending { it.latest }
            )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Which list the full-screen viewer is showing (set when a photo is opened).
    private val _viewerItems = MutableStateFlow<List<LocalMedia>>(emptyList())
    val viewerItems: StateFlow<List<LocalMedia>> = _viewerItems
    var viewerStart: Int = 0
    var openFolderName: String = ""

    fun openViewer(list: List<LocalMedia>, index: Int) {
        _viewerItems.value = list
        viewerStart = index
    }

    fun refresh() {
        if (_loading.value) return
        viewModelScope.launch {
            _loading.value = true
            val fresh = repo.loadAll()
            _all.value = fresh
            val keys = fresh.map { it.key }.toHashSet()
            _viewerItems.value = _viewerItems.value.filter { it.key in keys }
            _trash.value = repo.loadTrashed()
            _loading.value = false
        }
    }

    fun toggle(key: String) {
        selectMode.value = true
        selection.value = selection.value.let { if (key in it) it - key else it + key }
    }

    fun enterSelectMode() { selectMode.value = true }
    fun selectAll(pool: List<LocalMedia>) { selectMode.value = true; selection.value = pool.map { it.key }.toSet() }
    fun clearSelection() { selection.value = emptySet(); selectMode.value = false }
    fun selectedMedia(): List<LocalMedia> = _all.value.filter { it.key in selection.value }
}
