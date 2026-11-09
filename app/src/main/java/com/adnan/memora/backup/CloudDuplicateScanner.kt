package com.adnan.memora.backup

import android.content.Context
import com.adnan.memora.R
import com.adnan.memora.telegram.TelegramClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Reads the storage channel and reports copies of the same file.
 *
 * Every uploaded file carries `memora:v1 ... sha256=<hash>` in its caption, so the channel itself can be
 * checked without trusting the local database. Two files with the same hash, or (when a caption has no hash)
 * the same name and size, are the same file.
 *
 * This is a read-only scan: nothing is uploaded, moved or deleted until the owner presses the remove button,
 * and the oldest copy of each group is always the one that is kept.
 */
object CloudDuplicateScanner {

    data class Group(
        val chatId: Long,
        val name: String,
        val size: Long,
        val keepId: Long,
        val extraIds: List<Long>,
        val takenMs: Long,
    )

    sealed interface State {
        data object Idle : State
        data class Scanning(val found: Int) : State
        data class Done(val groups: List<Group>, val extras: Int, val bytes: Long, val complete: Boolean = true) : State
        data class Working(val done: Int, val total: Int) : State
        data class Removed(val removed: Int, val binned: Int) : State
        data class Failed(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    fun reset() {
        val s = _state.value
        if (s !is State.Scanning && s !is State.Working) _state.value = State.Idle
    }

    fun scan(ctx: Context) {
        val app = ctx.applicationContext
        _state.value = State.Scanning(0)
        scope.launch {
            try {
                val tg = TelegramClient.get(app)
                if (!tg.awaitUsable()) { _state.value = State.Failed(app.getString(R.string.cdup_offline)); return@launch }
                val chatId = tg.storage.value?.chatId
                if (chatId == null) { _state.value = State.Failed(app.getString(R.string.cdup_no_storage)); return@launch }
                val scan = tg.scanHistory(chatId) { n -> _state.value = State.Scanning(n) }
                val docs = scan.docs
                val grouped = docs.sortedBy { it.messageId }.groupBy { d ->
                    d.sha ?: "n:${d.name.lowercase()}:${d.size}"
                }
                val groups = grouped.values.filter { it.size > 1 }.map { list ->
                    val kept = list.first()   // lowest message id = the copy that arrived first
                    Group(
                        chatId = kept.chatId, name = kept.name, size = kept.size,
                        keepId = kept.messageId, extraIds = list.drop(1).map { it.messageId },
                        takenMs = kept.takenMs,
                    )
                }
                _state.value = State.Done(
                    groups = groups,
                    extras = groups.sumOf { it.extraIds.size },
                    bytes = groups.sumOf { it.size * it.extraIds.size },
                    complete = scan.complete,
                )
            } catch (t: Throwable) {
                _state.value = State.Failed(t.message ?: app.getString(R.string.cdup_failed))
            }
        }
    }

    /**
     * Removes the extra copies. A copy the app knows about goes to the cloud bin (30 days, restorable);
     * a copy it does not track is deleted from Telegram directly, because there is no bin entry to keep.
     */
    fun removeExtras(ctx: Context) {
        val current = _state.value as? State.Done ?: return
        val app = ctx.applicationContext
        // Never remove anything from a channel the app could only partly read: an unread message
        // looks like an untracked copy, and deleting it would be permanent.
        if (!current.complete) {
            _state.value = State.Failed(app.getString(R.string.cdup_partial))
            return
        }
        val total = current.groups.sumOf { it.extraIds.size }
        scope.launch {
            val dao = BackupDb.get(app).dao()
            val known = dao.cloudEntries().map { it.messageId }.toHashSet()
            val tg = TelegramClient.get(app)
            val now = System.currentTimeMillis()
            var done = 0
            var binned = 0
            for (g in current.groups) {
                for (id in g.extraIds) {
                    _state.value = State.Working(done, total)
                    try {
                        if (id in known) {
                            dao.trashByMessage(g.chatId, id, now)   // restorable for 30 days
                            binned++
                        } else {
                            tg.deleteMessages(g.chatId, listOf(id))  // untracked: cannot be binned
                        }
                    } catch (_: Throwable) {
                        // leave it; the next scan will report it again
                    }
                    done++
                }
            }
            _state.value = State.Removed(done, binned)
        }
    }
}
