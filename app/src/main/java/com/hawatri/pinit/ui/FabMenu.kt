package com.hawatri.pinit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FabMenu(
    visible: Boolean,
    onDismiss: () -> Unit,
    onNewNoteClick: () -> Unit,
    onNewListClick: () -> Unit,
    onNewLocationClick: () -> Unit,
    onNewQRClick: () -> Unit,
    onNewAppListClick: () -> Unit,
    onNewLinkClick: () -> Unit,
    onNewContactClick: () -> Unit,
    onNewImageClick: () -> Unit,
    onNewPDFClick: () -> Unit = {},
    onNewAudioClick: () -> Unit = {},
    onImportIcsClick: () -> Unit = {},
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
        MenuItem("New image", Icons.Filled.Image),
        MenuItem("New PDF", Icons.Filled.PictureAsPdf),
        MenuItem("Record audio", Icons.Filled.Mic),
        MenuItem("Import .ics", Icons.Filled.CalendarMonth)
    )

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(
            initialScale = 0.8f,
            transformOrigin = TransformOrigin(1f, 1f),
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn(animationSpec = tween(150)) + expandVertically(
            expandFrom = Alignment.Bottom,
            animationSpec = tween(220)
        ),
        exit = scaleOut(
            targetScale = 0.85f,
            transformOrigin = TransformOrigin(1f, 1f),
            animationSpec = tween(120)
        ) + fadeOut(animationSpec = tween(120)) + shrinkVertically(
            shrinkTowards = Alignment.Bottom,
            animationSpec = tween(160)
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
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
                            when (item.text) {
                                "New note" -> onNewNoteClick()
                                "New list" -> onNewListClick()
                                "New location" -> onNewLocationClick()
                                "New QR" -> onNewQRClick()
                                "New app list" -> onNewAppListClick()
                                "New link" -> onNewLinkClick()
                                "New contact" -> onNewContactClick()
                                "New image" -> onNewImageClick()
                                "New PDF" -> onNewPDFClick()
                                "Record audio" -> onNewAudioClick()
                                "Import .ics" -> onImportIcsClick()
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
}

data class MenuItem(val text: String, val icon: ImageVector)