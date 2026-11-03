package com.adnan.memora.backup

import android.content.Context
import com.adnan.memora.gallery.LocalMedia
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DupGroup(val hash: String, val items: List<LocalMedia>)

sealed class DupState {
    object Idle : DupState()
    class Scanning(val done: Int, val total: Int) : DupState()
    class Done(val groups: List<DupGroup>) : DupState()
}

/** Finds byte-identical files on the phone (same SHA-256), even with different names or folders. It NEVER deletes anything. */
object DuplicateScanner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _state = MutableStateFlow<DupState>(DupState.Idle)
    val state: StateFlow<DupState> = _state

    fun start(ctx: Context, all: List<LocalMedia>) {
        if (job?.isActive == true) return
        val app = ctx.applicationContext
        job = scope.launch {
            // Only files that share a size with another file can be identical, so only those are hashed.
            val candidates = all.groupBy { it.size }.filter { it.value.size > 1 }.values.flatten()
            val total = candidates.size
            _state.value = DupState.Scanning(0, total)
            val dao = BackupDb.get(app).dao()
            val hashes = HashMap<String, String>()
            candidates.forEachIndexed { i, m ->
                if (!isActive) return@launch
                try {
                    val cached = dao.cachedHash(m.key)
                    val h = if (cached != null && cached.size == m.size && cached.modified == m.dateModified) cached.sha256
                    else Hashing.hashOnly(app, m.uri).also { dao.putHash(HashCacheEntry(m.key, m.size, m.dateModified, it)) }
                    hashes[m.key] = h
                } catch (_: Exception) {
                    // unreadable or removed file: skip it
                }
                _state.value = DupState.Scanning(i + 1, total)
            }
            val groups = candidates.filter { it.key in hashes }
                .groupBy { hashes[it.key]!! }
                .filter { it.value.size > 1 }
                .map { (h, l) -> DupGroup(h, l.sortedBy { it.dateTaken }) }
                .sortedByDescending { it.items.size * it.items[0].size }
            _state.value = DupState.Done(groups)
        }
    }

    fun cancel() { job?.cancel(); _state.value = DupState.Idle }

    /** Drops deleted items from the shown results. */
    fun prune(deletedKeys: Set<String>) {
        val s = _state.value
        if (s is DupState.Done) {
            _state.value = DupState.Done(
                s.groups.map { g -> DupGroup(g.hash, g.items.filter { it.key !in deletedKeys }) }.filter { it.items.size > 1 }
            )
        }
    }
}
