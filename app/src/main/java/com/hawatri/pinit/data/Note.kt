package com.hawatri.pinit.data

import com.hawatri.pinit.ui.FormatRange
import java.util.UUID

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val text: String,
    val formatRanges: List<FormatRange>,
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)