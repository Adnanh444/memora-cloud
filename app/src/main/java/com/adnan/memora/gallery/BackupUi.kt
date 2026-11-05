package com.adnan.memora.gallery

import com.adnan.memora.data.BackupState

/** What the backup strip shows. The real backup engine (Phase 4) will feed this; until then it honestly says "not set up". */
enum class BackupPhase { NOT_SET_UP, IDLE, RUNNING, PAUSED }

data class BackupSummary(
    val phase: BackupPhase = BackupPhase.NOT_SET_UP,
    val done: Int = 0,
    val total: Int = 0,
    val currentName: String? = null,
    val currentProgress: Float = 0f,
    val paused: Boolean = false,
    val waiting: Int = 0,
    val attention: Int = 0,
    val needsConfirmation: Int = 0,
    val stage: String? = null,
)

/** Per-item cloud state shown as a small badge on thumbnails. */
data class ItemBackup(val state: BackupState, val progress: Float = 0f, val duplicate: Boolean = false)
