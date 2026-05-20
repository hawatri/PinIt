package com.hawatri.pinit.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hawatri.pinit.ui.FormatRange
import java.util.UUID

object NoteType {
    const val TEXT = "text"
    const val LIST = "list"
    const val QR = "qr"
    const val LINK = "link"
    const val CONTACT = "contact"
    const val LOCATION = "location"
    const val APPLIST = "applist"
    const val IMAGE = "image"
    const val PDF = "pdf"
    const val AUDIO = "audio"
}

object NoteColors {
    const val NONE = ""
    const val RED = "#FFCDD2"
    const val ORANGE = "#FFE0B2"
    const val YELLOW = "#FFF9C4"
    const val GREEN = "#DCEDC8"
    const val TEAL = "#B2DFDB"
    const val BLUE = "#BBDEFB"
    const val PURPLE = "#E1BEE7"
    const val PINK = "#F8BBD0"
    const val BROWN = "#D7CCC8"
    const val GRAY = "#CFD8DC"

    val all = listOf(NONE, RED, ORANGE, YELLOW, GREEN, TEAL, BLUE, PURPLE, PINK, BROWN, GRAY)
}

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val text: String,
    val formatRanges: List<FormatRange>,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isList: Boolean = false,
    val noteType: String = NoteType.TEXT,
    val colorHex: String? = null,
    val isLocked: Boolean = false,
    val labels: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val reminderText: String? = null
)