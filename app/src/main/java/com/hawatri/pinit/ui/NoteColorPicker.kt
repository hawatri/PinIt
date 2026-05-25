package com.hawatri.pinit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hawatri.pinit.data.NoteColors

@Composable
fun NoteColorPicker(
    selectedColor: String?,
    onColorSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(
                imageVector = Icons.Filled.FormatColorFill,
                contentDescription = "Note Color",
                tint = if (selectedColor.isNullOrBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                       else Color(android.graphics.Color.parseColor(selectedColor))
            )
        }

        if (expanded) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(NoteColors.all) { colorHex ->
                    val isSelected = selectedColor == colorHex || (colorHex == NoteColors.NONE && selectedColor.isNullOrBlank())
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (colorHex.isBlank()) MaterialTheme.colorScheme.surfaceVariant
                                else Color(android.graphics.Color.parseColor(colorHex))
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable {
                                onColorSelected(colorHex)
                                expanded = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (colorHex.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.DarkGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact color picker that opens as a popup — fits inside a TopAppBar's actions row.
 * Use this on screens that don't have a bottom toolbar.
 */
@Composable
fun ColorPickerMenuButton(
    selectedColor: String?,
    onColorSelected: (String) -> Unit,
    iconTint: Color? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Filled.FormatColorFill,
                contentDescription = "Note Color",
                tint = when {
                    !selectedColor.isNullOrBlank() -> Color(android.graphics.Color.parseColor(selectedColor))
                    iconTint != null -> iconTint
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NoteColors.all.forEach { colorHex ->
                    val isSelected = selectedColor == colorHex || (colorHex == NoteColors.NONE && selectedColor.isNullOrBlank())
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (colorHex.isBlank()) MaterialTheme.colorScheme.surfaceVariant
                                else Color(android.graphics.Color.parseColor(colorHex))
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable {
                                onColorSelected(colorHex)
                                expanded = false
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                Icons.Filled.Check, null,
                                tint = if (colorHex.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else Color.DarkGray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
