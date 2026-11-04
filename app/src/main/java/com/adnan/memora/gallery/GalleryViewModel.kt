package com.adnan.memora.gallery

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.widget.Toast
import com.adnan.memora.R
import com.adnan.memora.backup.BackupDb
import com.adnan.memora.cloud.CloudUri
import com.adnan.memora.org.AlbumEntity
import com.adnan.memora.org.AlbumItemEntity
import com.adnan.memora.org.OrgDb
import com.adnan.memora.org.OrgRepo
import com.adnan.memora.backup.toCloudMedia

enum class CollectionKind { FAVORITES, ARCHIVE, ALBUM }

data class AlbumSummary(val id: String, val name: String, val count: Int, val cover: LocalMedia?)

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

    // ---- favorites, archive, albums (identified by SHA-256) ----
    private val orgDao = OrgDb.get(app).dao()
    val favoriteShas: StateFlow<Set<String>> = orgDao.favorites().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val archivedShas: StateFlow<Set<String>> = orgDao.archived().map { it.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    val albums: StateFlow<List<AlbumEntity>> = orgDao.albums()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val albumItems: StateFlow<List<AlbumItemEntity>> = orgDao.albumItems()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** key (phone uri or cloud uri) -> SHA-256, for every item whose fingerprint is known. */
    val shaIndex: StateFlow<Map<String, String>> =
        combine(BackupDb.get(app).dao().observeShaRows(), com.adnan.memora.backup.BackupEngine.cloud) { rows, cloud ->
            val m = HashMap<String, String>(rows.size + cloud.size)
            rows.forEach { m[it.uri] = it.sha256 }
            cloud.forEach { e -> e.sha256?.let { m[CloudUri.thumb(e.chatId, e.messageId).toString()] = it } }
            m as Map<String, String>
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Photos feed: everything except archived items. */
    val photosFeed: StateFlow<List<LocalMedia>> = combine(_all, archivedShas, shaIndex) { all, arch, idx ->
        if (arch.isEmpty()) all else all.filter { idx[it.key] !in arch }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val cloudFeed: StateFlow<List<LocalMedia>> = combine(cloudMedia, archivedShas, shaIndex) { cl, arch, idx ->
        if (arch.isEmpty()) cl else cl.filter { idx[it.key] !in arch }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private fun pick(all: List<LocalMedia>, cloud: List<LocalMedia>, shas: Set<String>, idx: Map<String, String>): List<LocalMedia> {
        if (shas.isEmpty()) return emptyList()
        val local = all.filter { idx[it.key] in shas }
        val have = local.mapNotNull { idx[it.key] }.toHashSet()
        val cl = cloud.filter { val s = idx[it.key]; s != null && s in shas && s !in have }
        return (local + cl).sortedByDescending { it.dateTaken }
    }

    val favoriteMedia: StateFlow<List<LocalMedia>> = combine(_all, cloudMedia, favoriteShas, shaIndex) { a, c, s, i -> pick(a, c, s, i) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val archiveMedia: StateFlow<List<LocalMedia>> = combine(_all, cloudMedia, archivedShas, shaIndex) { a, c, s, i -> pick(a, c, s, i) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun albumMedia(albumId: String): StateFlow<List<LocalMedia>> =
        combine(_all, cloudMedia, albumItems, shaIndex) { a, c, items, i ->
            pick(a, c, items.filter { it.albumId == albumId }.map { it.sha }.toSet(), i)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val albumSummaries: StateFlow<List<AlbumSummary>> = combine(albums, albumItems, _all, cloudMedia, shaIndex) { al, items, a, c, i ->
        al.map { album ->
            val media = pick(a, c, items.filter { it.albumId == album.id }.map { it.sha }.toSet(), i)
            AlbumSummary(album.id, album.name, media.size, media.firstOrNull())
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // which collection screen is open (set before navigating)
    var collectionKind: CollectionKind = CollectionKind.FAVORITES
    var collectionAlbumId: String = ""

    private fun toast(res: Int) { Toast.makeText(getApplication<Application>(), res, Toast.LENGTH_SHORT).show() }

    fun selectedAny(): List<LocalMedia> =
        _all.value.filter { it.key in selection.value } + cloudMedia.value.filter { it.key in selection.value }

    fun setFavorite(items: List<LocalMedia>, on: Boolean) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            OrgRepo.favorite(app, OrgRepo.shasFor(app, items, shaIndex.value), on)
            toast(if (on) R.string.og_fav_added else R.string.og_fav_removed)
        }
    }

    fun setArchived(items: List<LocalMedia>, on: Boolean) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            OrgRepo.archive(app, OrgRepo.shasFor(app, items, shaIndex.value), on)
            toast(if (on) R.string.og_archived else R.string.og_unarchived)
        }
    }

    fun addToAlbum(items: List<LocalMedia>, albumId: String) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            OrgRepo.addToAlbum(app, albumId, OrgRepo.shasFor(app, items, shaIndex.value))
            toast(R.string.og_added_album)
        }
    }

    fun createAlbum(name: String, items: List<LocalMedia> = emptyList()) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val id = OrgRepo.createAlbum(app, name)
            if (items.isNotEmpty()) {
                OrgRepo.addToAlbum(app, id, OrgRepo.shasFor(app, items, shaIndex.value))
                toast(R.string.og_added_album)
            }
        }
    }

    fun removeFromAlbum(albumId: String, items: List<LocalMedia>) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            OrgRepo.removeFromAlbum(app, albumId, OrgRepo.shasFor(app, items, shaIndex.value))
            toast(R.string.og_removed_album)
        }
    }

    fun renameAlbum(albumId: String, name: String) {
        viewModelScope.launch { OrgRepo.renameAlbum(getApplication<Application>(), albumId, name) }
    }

    fun deleteAlbum(albumId: String) {
        viewModelScope.launch { OrgRepo.deleteAlbum(getApplication<Application>(), albumId) }
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
