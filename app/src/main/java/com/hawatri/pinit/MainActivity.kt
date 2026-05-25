package com.hawatri.pinit

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
import androidx.core.view.WindowCompat
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val sharedText = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        @Suppress("DEPRECATION")
        val sharedImageUri = if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
        } else null

        val widgetAction = intent?.getStringExtra("WIDGET_ACTION")
        val widgetOpenNoteId = intent?.getStringExtra("WIDGET_OPEN_NOTE_ID")

        @Suppress("DEPRECATION")
        val sharedIcsUri = if (intent?.action == Intent.ACTION_SEND &&
            (intent.type == "text/calendar" || intent.type == "application/ics")) {
            intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString()
        } else null

        val themeFlow = MutableStateFlow(AppPreferences.getThemeMode(this))
        themeModeFlow = themeFlow

        setContent {
            val themeMode by themeFlow.collectAsState()
            PinItTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PinItApp(
                        sharedText = sharedText,
                        sharedImageUri = sharedImageUri,
                        sharedIcsUri = sharedIcsUri,
                        widgetAction = widgetAction,
                        widgetOpenNoteId = widgetOpenNoteId,
                        currentTheme = themeMode,
                        onThemeChange = { mode ->
                            AppPreferences.setThemeMode(this@MainActivity, mode)
                            themeFlow.value = mode
                        }
                    )
                }
            }
        }
    }

    companion object {
        var themeModeFlow: MutableStateFlow<ThemeMode>? = null
    }
}
