package com.hawatri.pinit.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hawatri.pinit.data.AppPreferences

/**
 * One-time banner shown after a fresh install or upgrade. Reads the current
 * versionName/versionCode from PackageInfo at runtime so the version line is
 * always accurate without manual editing per release. Disappears once the
 * user taps × or "Show Changelog" — the seen versionCode is stored in
 * AppPreferences so it never reappears for that build.
 *
 * ## Per-release authoring
 *
 * Update [CHANGELOG_NOTES] with bullets for the new release, and optionally
 * [CHANGELOG_URL] if you want "Show Changelog" to open something other than
 * GitHub's auto-resolved latest release.
 */

/** Bullet list shown inline so users can read what's new without leaving the app. */
private val CHANGELOG_NOTES: List<String> = listOf(
    "Fixed double-rendering animation when switching tabs.",
    "Long-press any toolbar icon to see what it does.",
    "Multiple alarm reminders per note.",
    "Fixed format toolbar squeeze when writing long notes.",
    "Share PDFs, images, and audio directly into PinIt."
)

/** Where "Show Changelog" goes when the user wants the full release page. */
private const val CHANGELOG_URL = "https://github.com/hawatri/PinIt/releases/latest"

@Composable
fun PostUpdateBanner(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val info = remember {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) { null }
    }
    val versionName = info?.versionName ?: ""
    val versionCode = if (info == null) 0 else {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode.toInt()
        else @Suppress("DEPRECATION") info.versionCode
    }
    val lastSeen = remember { AppPreferences.getLastSeenVersionCode(context) }

    var visible by remember { mutableStateOf(versionCode > 0 && lastSeen != versionCode) }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                        Text(
                            text = "PinIt updated to v$versionName (Build #$versionCode)",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (CHANGELOG_NOTES.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "What's new",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            CHANGELOG_NOTES.forEach { line ->
                                Text(
                                    text = "•  $line",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 1.dp)
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = {
                            AppPreferences.setLastSeenVersionCode(context, versionCode)
                            visible = false
                        }
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CHANGELOG_URL)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) { /* no browser available */ }
                        AppPreferences.setLastSeenVersionCode(context, versionCode)
                        visible = false
                    }) {
                        Text("Show Full Changelog")
                    }
                }
            }
        }
    }
}
