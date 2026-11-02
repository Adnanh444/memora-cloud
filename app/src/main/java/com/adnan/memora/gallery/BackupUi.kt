package com.adnan.memora.gallery

import com.adnan.memora.data.BackupState

/** What the backup strip shows. Fed by the backup engine. */
enum class BackupPhase { NOT_SET_UP, IDLE, RUNNING, PAUSED }

data class BackupSummary(
    val phase: BackupPhase = BackupPhase.NOT_SET_UP,
    val done: Int = 0,
    val total: Int = 0,
    val currentName: String? = null,
    val currentProgress: Float = 0f,
    val failed: Int = 0,
)

/** Per-item cloud state shown as a small badge on thumbnails. */
data class ItemBackup(val state: BackupState, val progress: Float = 0f)
