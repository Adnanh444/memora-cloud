package com.adnan.memora.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.adnan.memora.backup.toCloudMedia

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
    val backupSummary: StateFlow<BackupSummary> = com.adnan.memora.backup.BackupEngine.summary
    val itemBackup: StateFlow<Map<String, ItemBackup>> = com.adnan.memora.backup.BackupEngine.items

    val folders: StateFlow<List<FolderInfo>> = _all.map { list ->
        list.groupBy { it.bucket }
            .map { (name, l) -> FolderInfo(name, l.size, l.first(), l.maxOf { it.dateTaken }) }
            .sortedWith(
                compareByDescending<FolderInfo> { it.name.equals("Camera", ignoreCase = true) }
                    .thenByDescending { it.latest }
            )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _viewerItems = MutableStateFlow<List<LocalMedia>>(emptyList())
    val viewerItems: StateFlow<List<LocalMedia>> = _viewerItems

    /** Backed-up files as shown in the Cloud tab (previews come from Telegram on demand). */
    val cloudMedia: StateFlow<List<LocalMedia>> = com.adnan.memora.backup.BackupEngine.cloud
        .map { list -> list.map { it.toCloudMedia() } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val cloudTrash: StateFlow<List<TrashedMedia>> = com.adnan.memora.backup.BackupEngine.cloudTrash
        .map { list -> list.map { TrashedMedia(it.toCloudMedia(), it.trashedAt + 30L * 24 * 3600 * 1000) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        // when a cloud item leaves the cloud (e.g. moved to the cloud bin) close it in the viewer list
        viewModelScope.launch {
            cloudMedia.collect { c ->
                val keys = c.map { it.key }.toHashSet()
                _viewerItems.value = _viewerItems.value.filter { !com.adnan.memora.cloud.CloudUri.isCloud(it.uri) || it.key in keys }
            }
        }
    }

    // Which list the full-screen viewer is showing (set when a photo is opened).
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
            _viewerItems.value = _viewerItems.value.filter { it.key in keys || com.adnan.memora.cloud.CloudUri.isCloud(it.uri) }
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
    fun selectedCloud(): List<LocalMedia> = cloudMedia.value.filter { it.key in selection.value }
}
