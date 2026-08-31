package com.hawatri.pinit.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.hawatri.pinit.backup.BackupSyncManager
import com.hawatri.pinit.backup.GoogleAuthManager
import com.hawatri.pinit.data.AppPreferences
import kotlinx.coroutines.launch
import com.hawatri.pinit.R
import androidx.compose.ui.res.stringResource

private const val STALE_THRESHOLD_MS = 14L * 24 * 60 * 60 * 1000   // 14 days
private const val SNOOZE_AFTER_REMIND_LATER_MS = 3L * 24 * 60 * 60 * 1000   // 3 days

/**
 * One-time-per-launch dialog that nags the user when their last successful
 * backup is more than [STALE_THRESHOLD_MS] old AND they have local changes
 * that need backing up. Three actions:
 *  - **Back up now** → kicks off [BackupSyncManager.backupNow] (or routes to
 *    sign-in if not signed in), records the dismissal so we don't re-prompt
 *    again on the same launch.
 *  - **Remind me later** → snoozes for [SNOOZE_AFTER_REMIND_LATER_MS].
 *  - **Don't ask again** → flips a permanent suppression flag.
 *
 * Only shows once per process launch. The persistent yellow banner remains
 * visible regardless — this dialog is the *additional* nudge for users who
 * have ignored the banner long enough that data loss is becoming likely.
 */
@Composable
fun StaleBackupDialog(onNavigateToSignIn: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val syncState by BackupSyncManager.state.collectAsState()

    // Decide once on first composition whether to show the dialog. Subsequent
    // recompositions don't re-trigger — the user dealt with it on this launch.
    var show by remember {
        val now = System.currentTimeMillis()
        val lastBackup = AppPreferences.getLastBackupAt(context)
        val lastModified = AppPreferences.getLastModifiedAt(context)
        val dismissedAt = AppPreferences.getStaleDialogDismissedAt(context)
        val suppressed = AppPreferences.isStaleDialogSuppressedForever(context)
        val remindersEnabled = AppPreferences.isBackupRemindersEnabled(context)

        val hasUnsavedChanges = lastModified > 0L && lastModified > lastBackup
        val backupIsStale = lastBackup == 0L || (now - lastBackup) > STALE_THRESHOLD_MS
        val recentlySnoozed = (now - dismissedAt) < SNOOZE_AFTER_REMIND_LATER_MS

        mutableStateOf(remindersEnabled && !suppressed && hasUnsavedChanges && backupIsStale && !recentlySnoozed)
    }

    if (!show) return

    val signedIn = remember { GoogleAuthManager.currentAccount(context) != null }
    val isWorking = syncState is BackupSyncManager.State.Working

    // Auto-close once the in-flight backup we kicked off succeeds, so the user
    // doesn't have to dismiss manually after tapping "Back up now".
    LaunchedEffect(syncState) {
        if (syncState is BackupSyncManager.State.Success) {
            show = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            // Tapping outside == "Remind me later"
            AppPreferences.setStaleDialogDismissedNow(context)
            show = false
        },
        title = { Text(stringResource(R.string.backup_your_notes)) },
        text = {
            val message = buildString {
                append(stringResource(R.string.stale_body_intro))
                append(stringResource(R.string.stale_body_risk))
                if (!signedIn) append(stringResource(R.string.stale_body_signin))
                else append(stringResource(R.string.stale_body_backup))
            }
            Text(message)
        },
        confirmButton = {
            TextButton(
                enabled = !isWorking,
                onClick = {
                    if (!signedIn) {
                        AppPreferences.setStaleDialogDismissedNow(context)
                        show = false
                        onNavigateToSignIn()
                    } else {
                        scope.launch { BackupSyncManager.backupNow(context) }
                    }
                }
            ) {
                Text(if (!signedIn) stringResource(R.string.sign_in) else if (isWorking) stringResource(R.string.backup_backing_up) else stringResource(R.string.backup_now))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isWorking,
                onClick = {
                    AppPreferences.setStaleDialogDismissedNow(context)
                    show = false
                }
            ) {
                Text(stringResource(R.string.backup_remind_me_later))
            }
            TextButton(
                enabled = !isWorking,
                onClick = {
                    AppPreferences.setStaleDialogSuppressedForever(context)
                    show = false
                }
            ) {
                Text(stringResource(R.string.backup_dont_ask_again))
            }
        }
    )
}
