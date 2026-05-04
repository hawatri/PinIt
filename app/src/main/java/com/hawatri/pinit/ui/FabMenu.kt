package com.hawatri.pinit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FabMenu(
    onDismiss: () -> Unit,
    onNewNoteClick: () -> Unit,
    onNewListClick: () -> Unit,
    onNewLocationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuItems = listOf(
        MenuItem("New note", Icons.Filled.NoteAdd),
        MenuItem("New list", Icons.Filled.FormatListBulleted),
        MenuItem("New location", Icons.Filled.LocationOn),
        MenuItem("New QR", Icons.Filled.QrCodeScanner),
        MenuItem("New app list", Icons.Filled.Apps),
        MenuItem("New link", Icons.Filled.Link),
        MenuItem("New contact", Icons.Filled.PersonAdd),
        MenuItem("New image", Icons.Filled.Image)
    )

    Column(
        modifier = modifier
            .width(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 8.dp)
    ) {
        menuItems.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        // Updated logic to handle different actions
                        when (item.text) {
                            "New note" -> onNewNoteClick()
                            "New list" -> onNewListClick()
                            "New location" -> onNewLocationClick()
                        }
                        onDismiss()
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.text,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = item.text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        }
    }
}

data class MenuItem(val text: String, val icon: ImageVector)