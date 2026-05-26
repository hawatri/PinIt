package com.hawatri.pinit.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hawatri.pinit.backup.BackupSyncManager
import com.hawatri.pinit.backup.GoogleAuthManager
import com.hawatri.pinit.data.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToSignIn: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState by BackupSyncManager.state.collectAsState()
    val signedIn = remember { GoogleAuthManager.currentAccount(context) != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Appearance
            SettingsSection(title = "Appearance") {
                ThemeOption(
                    label = "Light",
                    icon = Icons.Filled.LightMode,
                    selected = currentTheme == ThemeMode.LIGHT,
                    onClick = { onThemeChange(ThemeMode.LIGHT) }
                )
                ThemeOption(
                    label = "Dark",
                    icon = Icons.Filled.DarkMode,
                    selected = currentTheme == ThemeMode.DARK,
                    onClick = { onThemeChange(ThemeMode.DARK) }
                )
                ThemeOption(
                    label = "System default",
                    icon = Icons.Filled.SettingsBrightness,
                    selected = currentTheme == ThemeMode.SYSTEM,
                    onClick = { onThemeChange(ThemeMode.SYSTEM) }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Backup
            SettingsSection(title = "Backup") {
                Column(modifier = Modifier.padding(16.dp)) {
                    BackupActionButton(
                        title = "Take online backup",
                        subtitle = if (signedIn) "Upload to your Google Drive" else "Sign in with Google first",
                        icon = Icons.Filled.CloudUpload,
                        enabled = syncState !is BackupSyncManager.State.Working,
                        primary = true,
                        onClick = {
                            if (!signedIn) {
                                onNavigateToSignIn()
                            } else {
                                scope.launch { BackupSyncManager.backupNow(context) }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    BackupActionButton(
                        title = "Take offline backup",
                        subtitle = "Save a .pinit file to Download/PinIt/",
                        icon = Icons.Filled.SaveAlt,
                        enabled = syncState !is BackupSyncManager.State.Working,
                        primary = false,
                        onClick = {
                            scope.launch { BackupSyncManager.backupOfflineNow(context) }
                        }
                    )
                    SyncStatusInline(state = syncState)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // About
            SettingsSection(title = "About") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Version", color = MaterialTheme.colorScheme.onSurface)
                    Text("1.0", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:kiahawatri@gmail.com")
                                putExtra(Intent.EXTRA_SUBJECT, "PinIt — Issue / Feedback")
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "Describe the issue or share feedback below.\n\n" +
                                        "----\nApp version: 1.0\n"
                                )
                            }
                            try {
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    "No mail app installed",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.BugReport,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Report an issue",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "kiahawatri@gmail.com",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        text = title,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(content = content)
    }
}

@Composable
private fun ThemeOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun BackupActionButton(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    primary: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (primary)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surface
    val contentColor = if (primary)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (primary)
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = if (primary) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (primary) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f)
                        else MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    null,
                    tint = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
                Text(subtitle, fontSize = 12.sp, color = subtitleColor)
            }
            Icon(
                Icons.Filled.ChevronRight,
                null,
                tint = subtitleColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun SyncStatusInline(state: BackupSyncManager.State) {
    if (state is BackupSyncManager.State.Idle) return
    Spacer(modifier = Modifier.height(12.dp))
    val (icon, msg, tint) = when (state) {
        is BackupSyncManager.State.Working -> Triple(Icons.Filled.Sync, state.message, MaterialTheme.colorScheme.primary)
        is BackupSyncManager.State.Success -> Triple(Icons.Filled.CheckCircle, state.message, MaterialTheme.colorScheme.primary)
        is BackupSyncManager.State.Error -> Triple(Icons.Filled.Error, state.message, MaterialTheme.colorScheme.error)
        else -> return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(msg, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

