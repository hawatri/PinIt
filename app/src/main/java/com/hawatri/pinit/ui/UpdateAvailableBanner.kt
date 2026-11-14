package com.hawatri.pinit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hawatri.pinit.R
import com.hawatri.pinit.data.AppPreferences
import com.hawatri.pinit.util.UpdateChecker

/**
 * "New version available" card on Home. Checks once per Home visit; hidden once
 * the user closes it for that release. The background [UpdateCheckWorker] covers
 * users who don't open the app, via a notification.
 */
@Composable
fun UpdateAvailableBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }

    LaunchedEffect(Unit) {
        val found = UpdateChecker.check(context)
        if (found != null && AppPreferences.getUpdateBannerDismissed(context) != found.id) update = found
    }

    val current = update
    AnimatedVisibility(
        visible = current != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        if (current == null) return@AnimatedVisibility
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Row(
                modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.SystemUpdate, null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 12.dp, end = 12.dp)
                )
                Column(Modifier.weight(1f).padding(top = 10.dp)) {
                    Text(
                        stringResource(R.string.update_available_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        current.versionName?.let { stringResource(R.string.update_available_text_version, it) }
                            ?: stringResource(R.string.update_available_text),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                    )
                    Button(onClick = { UpdateChecker.openUpdate(context, current) }) {
                        Text(stringResource(R.string.action_update_now))
                    }
                }
                IconButton(onClick = {
                    AppPreferences.setUpdateBannerDismissed(context, current.id)
                    update = null
                }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_dismiss))
                }
            }
        }
    }
}
