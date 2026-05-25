package com.hawatri.pinit.ui

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.hawatri.pinit.backup.BackupSyncManager
import com.hawatri.pinit.backup.GoogleAuthManager
import com.hawatri.pinit.data.AppPreferences
import kotlinx.coroutines.launch

/**
 * First-launch onboarding pager. Shown once, then suppressed via
 * [AppPreferences.setOnboardingDone]. Five pages:
 *
 *   1. Welcome — what PinIt does
 *   2. Feature showcase — 10 note types
 *   3. Sign-in (optional) — Google Sign-In or "Continue as guest"
 *   4. Notification permission — POST_NOTIFICATIONS (Android 13+)
 *   5. Exact-alarm permission — SCHEDULE_EXACT_ALARM (Android 12+)
 *
 * Pages 4 and 5 self-skip on Android versions where the permission isn't required,
 * so the user never sees them — but the page count stays at 5 internally for
 * deterministic indices. The buttons advance via [next] which knows how to skip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 5 })

    val needsNotifPermission = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    val needsExactAlarm = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            am?.canScheduleExactAlarms() == false
        } else false
    }

    fun next() {
        scope.launch {
            var target = pagerState.currentPage + 1
            // Skip notif page if not needed
            if (target == 3 && !needsNotifPermission) target++
            if (target == 4 && !needsExactAlarm) target++
            if (target >= 5) {
                AppPreferences.setOnboardingDone(context, true)
                onComplete()
            } else {
                pagerState.animateScrollToPage(target)
            }
        }
    }

    fun skipToFinish() {
        AppPreferences.setOnboardingDone(context, true)
        onComplete()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar — only shows skip on early pages
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                AnimatedVisibility(
                    visible = pagerState.currentPage < 2,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    TextButton(onClick = ::skipToFinish) { Text("Skip") }
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                userScrollEnabled = false
            ) { page ->
                when (page) {
                    0 -> WelcomePage()
                    1 -> FeatureShowcasePage()
                    2 -> SignInPage(onContinue = ::next)
                    3 -> NotificationPermissionPage(onContinue = ::next)
                    4 -> ExactAlarmPage(onContinue = ::next)
                }
            }

            PagerIndicator(
                pageCount = 5,
                currentPage = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            // Continue button — sign-in/permission pages have their own actions, the
            // first two are just informational so the global Continue advances them.
            if (pagerState.currentPage < 2) {
                Button(
                    onClick = ::next,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 28.dp)
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp)
                ) {
                    Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun PagerIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { i ->
            val active = i == currentPage
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (active) 24.dp else 8.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                    )
            )
        }
    }
}

// --- Page 1: Welcome -------------------------------------------------------

@Composable
private fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.PushPin,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(72.dp)
            )
        }
        Spacer(Modifier.height(40.dp))
        Text(
            "Welcome to PinIt",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Pin everything that matters — notes, lists, links, contacts, locations, QR codes and more — straight to your notification shade and home screen.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

// --- Page 2: Feature showcase ----------------------------------------------

@Composable
private fun FeatureShowcasePage() {
    val features = listOf(
        Triple(Icons.Filled.Notes, "Notes", "Rich-text with formatting"),
        Triple(Icons.Filled.CheckBox, "Checklists", "Tap-to-tick from the shade"),
        Triple(Icons.Filled.QrCode2, "QR codes", "Scan, save, regenerate"),
        Triple(Icons.Filled.Link, "Links", "Auto-fetched preview cards"),
        Triple(Icons.Filled.Phone, "Contacts", "One-tap dial"),
        Triple(Icons.Filled.LocationOn, "Locations", "Map pin + navigate"),
        Triple(Icons.Filled.Apps, "App lists", "Curated app shortcuts"),
        Triple(Icons.Filled.Image, "Images", "Pin a photo to home"),
        Triple(Icons.Filled.PictureAsPdf, "PDFs", "First-page preview"),
        Triple(Icons.Filled.Mic, "Audio", "Voice memos with playback"),
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Everything you need,\nin one app",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Ten note types, each with its own dedicated UI.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        // 5 rows × 2 columns grid
        val rows = features.chunked(2)
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { (icon, title, subtitle) ->
                    FeatureTile(
                        icon = icon,
                        title = title,
                        subtitle = subtitle,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FeatureTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

// --- Page 3: Sign-in -------------------------------------------------------

@Composable
private fun SignInPage(onContinue: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signingIn by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val signInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        signingIn = false
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val signed = task.getResult(ApiException::class.java) ?: return@rememberLauncherForActivityResult
            AppPreferences.setUser(context, signed.displayName, signed.email)
            scope.launch {
                BackupSyncManager.signInAndSync(context)
                onContinue()
            }
        } catch (e: ApiException) {
            errorMsg = when (e.statusCode) {
                10 -> "App isn't registered with Google yet. You can sign in later from Settings."
                12501 -> null // user-cancelled, silent
                7 -> "Network error. Try again later."
                else -> "Sign-in failed (code ${e.statusCode})."
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CloudSync,
                null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(60.dp)
            )
        }
        Spacer(Modifier.height(32.dp))
        Text(
            "Sync across devices",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Sign in with Google to back up your notes to Drive. PinIt only sees the files it creates — your other Drive content stays private.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
        if (errorMsg != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                errorMsg!!,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = {
                errorMsg = null
                signingIn = true
                signInLauncher.launch(GoogleAuthManager.signInIntent(context))
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp),
            enabled = !signingIn
        ) {
            if (signingIn) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text("Signing in…", fontSize = 16.sp)
            } else {
                Icon(Icons.Filled.AccountCircle, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Sign in with Google", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue as guest")
        }
    }
}

// --- Page 4: Notification permission ---------------------------------------

@Composable
private fun NotificationPermissionPage(onContinue: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> onContinue() }

    PermissionPage(
        icon = Icons.Filled.Notifications,
        title = "Notification permission",
        description = "PinIt's purpose is to pin reminders and notes to your notification shade. Without this permission you won't see pinned items.",
        primaryLabel = "Grant permission",
        onPrimary = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onContinue()
            }
        },
        onSkip = onContinue
    )
}

// --- Page 5: Exact alarm permission ----------------------------------------

@Composable
private fun ExactAlarmPage(onContinue: () -> Unit) {
    val context = LocalContext.current
    PermissionPage(
        icon = Icons.Filled.Alarm,
        title = "Reminder alarms",
        description = "PinIt schedules exact alarms so reminders fire at the right time. Tap below to allow this in system settings, then come back to PinIt.",
        primaryLabel = "Open settings",
        onPrimary = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                runCatching { context.startActivity(intent) }
            }
            onContinue()
        },
        onSkip = onContinue
    )
}

@Composable
private fun PermissionPage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSkip: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(70.dp))
        }
        Spacer(Modifier.height(32.dp))
        Text(
            title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            description,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(26.dp)
        ) {
            Text(primaryLabel, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text("Maybe later")
        }
    }
}
