package com.hawatri.pinit.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hawatri.pinit.ui.FormatRange
import java.util.UUID

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val text: String,
    val formatRanges: List<FormatRange>,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false, // NEW FIELD
    val isList: Boolean = false, // <-- NEW FIELD
    val timestamp: Long = System.currentTimeMillis()
)