package com.hawatri.pinit.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hawatri.pinit.backup.BackupSyncManager
import com.hawatri.pinit.backup.GoogleAuthManager
import com.hawatri.pinit.data.AppPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.hawatri.pinit.R
import androidx.compose.ui.res.stringResource

/**
 * Persistent yellow banner shown on Home when the user has local changes that
 * have not been backed up. Comparison is `lastModifiedAt > lastBackupAt`. Tap
 * the action button to trigger an online backup if signed in, or jump to
 * sign-in otherwise.
 *
 * The banner re-evaluates every 30 seconds so it disappears within seconds of
 * a successful backup, without needing to wire SharedPreferences listeners.
 * Sync state is also observed so the button switches to stringResource(R.string.backup_backing_up) while
 * a backup is in flight.
 */
@Composable
fun UnbackedChangesBanner(
    onSignInClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState by BackupSyncManager.state.collectAsState()

    var lastModified by remember { mutableLongStateOf(AppPreferences.getLastModifiedAt(context)) }
    var lastBackup by remember { mutableLongStateOf(AppPreferences.getLastBackupAt(context)) }
    var remindersEnabled by remember { mutableStateOf(AppPreferences.isBackupRemindersEnabled(context)) }

    // Cheap polling — banner is mounted only on Home, prefs reads are O(1).
    LaunchedEffect(Unit) {
        while (true) {
            lastModified = AppPreferences.getLastModifiedAt(context)
            lastBackup = AppPreferences.getLastBackupAt(context)
            remindersEnabled = AppPreferences.isBackupRemindersEnabled(context)
            delay(2_000)
        }
    }

    val hasUnbackedChanges = remindersEnabled && lastModified > 0L && lastModified > lastBackup
    val signedIn = remember(syncState) { GoogleAuthManager.currentAccount(context) != null }
    val isBackingUp = syncState is BackupSyncManager.State.Working

    AnimatedVisibility(
        visible = hasUnbackedChanges,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        // Warning palette — yellow card, dark text. Stays readable in both
        // light and dark themes because we pick our own colours rather than
        // pulling from MaterialTheme (which would tint differently per scheme).
        val warnContainer = Color(0xFFFFF3CD)
        val warnContent = Color(0xFF664D03)
        val warnAccent = Color(0xFFB8860B)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = warnContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = warnAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                    Text(
                        text = stringResource(R.string.banner_unbacked_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = warnContent
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (signedIn)
                        stringResource(R.string.banner_unbacked_body)
                    else
                        stringResource(R.string.banner_unbacked_body_signin),
                    style = MaterialTheme.typography.bodySmall,
                    color = warnContent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        enabled = !isBackingUp,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = warnAccent,
                            contentColor = Color.White,
                            disabledContainerColor = warnAccent.copy(alpha = 0.5f)
                        ),
                        onClick = {
                            if (!signedIn) {
                                onSignInClick()
                            } else {
                                scope.launch { BackupSyncManager.backupNow(context) }
                            }
                        }
                    ) {
                        Text(
                            text = when {
                                isBackingUp -> stringResource(R.string.backup_backing_up)
                                !signedIn -> stringResource(R.string.banner_sign_in_to_back_up)
                                else -> stringResource(R.string.backup_now)
                            }
                        )
                    }
                }
            }
        }
    }
}
