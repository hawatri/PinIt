package com.hawatri.pinit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.hawatri.pinit.data.AppPreferences
import com.hawatri.pinit.data.BackupMode
import com.hawatri.pinit.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var backupMode by remember { mutableStateOf(AppPreferences.getBackupMode(context)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, "Back")
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
                BackupOption(
                    label = "Off",
                    description = "No backups taken",
                    icon = Icons.Filled.CloudOff,
                    selected = backupMode == BackupMode.OFF,
                    onClick = { backupMode = BackupMode.OFF; AppPreferences.setBackupMode(context, BackupMode.OFF) }
                )
                BackupOption(
                    label = "Offline",
                    description = "Save to local device storage",
                    icon = Icons.Filled.Save,
                    selected = backupMode == BackupMode.OFFLINE,
                    onClick = { backupMode = BackupMode.OFFLINE; AppPreferences.setBackupMode(context, BackupMode.OFFLINE) }
                )
                BackupOption(
                    label = "Online",
                    description = "Sync to cloud (sign-in required)",
                    icon = Icons.Filled.CloudUpload,
                    selected = backupMode == BackupMode.ONLINE,
                    onClick = { backupMode = BackupMode.ONLINE; AppPreferences.setBackupMode(context, BackupMode.ONLINE) }
                )
                Text(
                    "Backup support is being built. Your selection is saved and will activate once available.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
                )
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
            }
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
private fun BackupOption(
    label: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}
