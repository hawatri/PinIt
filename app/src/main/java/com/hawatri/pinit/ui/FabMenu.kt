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
import com.hawatri.pinit.R
import androidx.compose.ui.res.stringResource

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
    // Each item carries its own callback. Dispatch used to match on the label text,
    // which would break the moment those labels were translated.
    val menuItems = listOf(
        MenuItem(R.string.fab_new_note, Icons.Filled.NoteAdd, onNewNoteClick),
        MenuItem(R.string.fab_new_list, Icons.Filled.FormatListBulleted, onNewListClick),
        MenuItem(R.string.fab_new_location, Icons.Filled.LocationOn, onNewLocationClick),
        MenuItem(R.string.fab_new_qr, Icons.Filled.QrCodeScanner, onNewQRClick),
        MenuItem(R.string.fab_new_app_list, Icons.Filled.Apps, onNewAppListClick),
        MenuItem(R.string.fab_new_link, Icons.Filled.Link, onNewLinkClick),
        MenuItem(R.string.fab_new_contact, Icons.Filled.PersonAdd, onNewContactClick),
        MenuItem(R.string.fab_new_image, Icons.Filled.Image, onNewImageClick),
        MenuItem(R.string.fab_new_pdf, Icons.Filled.PictureAsPdf, onNewPDFClick),
        MenuItem(R.string.fab_record_audio, Icons.Filled.Mic, onNewAudioClick),
        MenuItem(R.string.fab_import_ics, Icons.Filled.CalendarMonth, onImportIcsClick)
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
                            item.onClick()
                            onDismiss()
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = stringResource(item.textRes),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = stringResource(item.textRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * One row of the FAB menu. [textRes] is a string resource so the label localizes,
 * and [onClick] carries the action so dispatch never depends on the displayed text.
 */
data class MenuItem(val textRes: Int, val icon: ImageVector, val onClick: () -> Unit)