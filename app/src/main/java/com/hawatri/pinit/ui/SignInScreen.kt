package com.hawatri.pinit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.hawatri.pinit.backup.BackupSyncManager
import com.hawatri.pinit.backup.GoogleAuthManager
import com.hawatri.pinit.data.AppPreferences
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var account by remember { mutableStateOf(GoogleAuthManager.currentAccount(context)) }
    val syncState by BackupSyncManager.state.collectAsState()
    val lastSyncAt by remember { derivedStateOf { AppPreferences.getLastSyncAt(context) } }
    var lastSyncDisplay by remember { mutableStateOf(formatLastSync(lastSyncAt)) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val signed = task.getResult(ApiException::class.java) ?: return@rememberLauncherForActivityResult
            AppPreferences.setUser(context, signed.displayName, signed.email)
            account = signed
            scope.launch {
                BackupSyncManager.signInAndSync(context)
                lastSyncDisplay = formatLastSync(AppPreferences.getLastSyncAt(context))
            }
        } catch (e: ApiException) {
            // Surface the failure so the user knows what's wrong instead of nothing happening.
            // Status code 10 = DEVELOPER_ERROR (OAuth client not configured for this package/SHA-1).
            // Status code 12501 = user-cancelled. Status code 7 = network failure.
            val msg = when (e.statusCode) {
                10 -> "Sign-in failed: app isn't registered with Google. The OAuth client ID for this package + SHA-1 hasn't been created in Google Cloud Console."
                12501 -> "Sign-in cancelled"
                7 -> "Sign-in failed: network error"
                else -> "Sign-in failed (code ${e.statusCode}): ${e.localizedMessage ?: "unknown"}"
            }
            BackupSyncManager.setError(msg)
        } catch (e: Exception) {
            BackupSyncManager.setError("Sign-in failed: ${e.localizedMessage ?: "unknown"}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (account != null) "Account" else "Sign in") },
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (account != null) Icons.Filled.Person else Icons.Filled.PersonOutline,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(48.dp)
                )
            }
            Spacer(Modifier.height(24.dp))

            if (account != null) {
                val acc = account!!
                Text(
                    acc.displayName ?: "PinIt user",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    acc.email ?: "",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))

                SyncStatusCard(syncState, lastSyncDisplay)

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        scope.launch {
                            BackupSyncManager.backupNow(context)
                            lastSyncDisplay = formatLastSync(AppPreferences.getLastSyncAt(context))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = syncState !is BackupSyncManager.State.Working
                ) {
                    Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Back up now")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            BackupSyncManager.restoreNow(context)
                            lastSyncDisplay = formatLastSync(AppPreferences.getLastSyncAt(context))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = syncState !is BackupSyncManager.State.Working
                ) {
                    Icon(Icons.Filled.CloudDownload, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Restore from Drive")
                }
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = {
                        scope.launch {
                            GoogleAuthManager.signOut(context)
                            AppPreferences.signOut(context)
                            account = null
                            BackupSyncManager.reset()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign out")
                }
            } else {
                Text(
                    "Sign in with Google to back up your notes, lists, labels and audio recordings to Drive. Backups land in My Drive › PinIt.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 24.dp)
                )
                Button(
                    onClick = { signInLauncher.launch(GoogleAuthManager.signInIntent(context)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign in with Google")
                }
                Spacer(Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Privacy", fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "PinIt only requests access to files it creates in your Drive. It cannot read or modify any other Drive files.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusCard(state: BackupSyncManager.State, lastSync: String) {
    val (icon, msg, tint) = when (state) {
        is BackupSyncManager.State.Working -> Triple(Icons.Filled.Sync, state.message, MaterialTheme.colorScheme.primary)
        is BackupSyncManager.State.Success -> Triple(Icons.Filled.CheckCircle, state.message, MaterialTheme.colorScheme.primary)
        is BackupSyncManager.State.Error -> Triple(Icons.Filled.Error, state.message, MaterialTheme.colorScheme.error)
        BackupSyncManager.State.Idle -> Triple(Icons.Filled.CloudDone, "Last sync: $lastSync", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(msg, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

private fun formatLastSync(ts: Long): String {
    if (ts <= 0L) return "never"
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))
}
