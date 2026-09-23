package com.hawatri.pinit

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.hawatri.pinit.data.AppPreferences
import com.hawatri.pinit.data.ThemeMode
import com.hawatri.pinit.ui.PinItApp
import com.hawatri.pinit.ui.theme.PinItTheme
import com.hawatri.pinit.util.LocaleHelper
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow

data class LaunchRequest(
    val id: Long = 0L,
    val widgetAction: String? = null,
    val widgetOpenNoteId: String? = null,
    val sharedText: String? = null,
    val sharedImageUri: String? = null,
    val sharedPdfUri: String? = null,
    val sharedAudioUri: String? = null,
    val sharedIcsUri: String? = null
)

class MainActivity : FragmentActivity() {

    /**
     * Applies the in-app language before any resource is resolved. Compose reads
     * strings through `LocalContext.current`, which is this Activity, so wrapping
     * here is what makes `stringResource` honour the picker.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        publishLaunch(intent)

        val themeFlow = MutableStateFlow(AppPreferences.getThemeMode(this))
        themeModeFlow = themeFlow

        setContent {
            val themeMode by themeFlow.collectAsState()
            val launch by launchRequest.collectAsState()
            PinItTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PinItApp(
                        sharedText = launch.sharedText,
                        sharedImageUri = launch.sharedImageUri,
                        sharedPdfUri = launch.sharedPdfUri,
                        sharedAudioUri = launch.sharedAudioUri,
                        sharedIcsUri = launch.sharedIcsUri,
                        widgetAction = launch.widgetAction,
                        widgetOpenNoteId = launch.widgetOpenNoteId,
                        launchGeneration = launch.id,
                        currentTheme = themeMode,
                        onThemeChange = { mode ->
                            AppPreferences.setThemeMode(this@MainActivity, mode)
                            themeFlow.value = mode
                        },
                        onLanguageChange = { tag ->
                            AppPreferences.setLanguageTag(this@MainActivity, tag)
                            recreate()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        publishLaunch(intent)
    }

    private fun publishLaunch(intent: Intent?) {
        launchRequest.value = extractLaunch(intent).copy(id = nextLaunchId++)
    }

    private fun extractLaunch(intent: Intent?): LaunchRequest {
        if (intent == null) return LaunchRequest()
        val sharedText = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        @Suppress("DEPRECATION")
        val sharedImageUri = if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
        } else null

        @Suppress("DEPRECATION")
        val sharedPdfUri = if (intent.action == Intent.ACTION_SEND && intent.type == "application/pdf") {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
        } else null

        @Suppress("DEPRECATION")
        val sharedAudioUri = if (intent.action == Intent.ACTION_SEND && intent.type?.startsWith("audio/") == true) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
        } else null

        @Suppress("DEPRECATION")
        val sharedIcsUri = if (intent.action == Intent.ACTION_SEND &&
            (intent.type == "text/calendar" || intent.type == "application/ics")) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
        } else null

        return LaunchRequest(
            widgetAction = intent.getStringExtra("WIDGET_ACTION"),
            widgetOpenNoteId = intent.getStringExtra("WIDGET_OPEN_NOTE_ID"),
            sharedText = sharedText,
            sharedImageUri = sharedImageUri,
            sharedPdfUri = sharedPdfUri,
            sharedAudioUri = sharedAudioUri,
            sharedIcsUri = sharedIcsUri
        )
    }

    companion object {
        var themeModeFlow: MutableStateFlow<ThemeMode>? = null
        private var nextLaunchId = 1L
        val launchRequest = MutableStateFlow(LaunchRequest())
    }
}
