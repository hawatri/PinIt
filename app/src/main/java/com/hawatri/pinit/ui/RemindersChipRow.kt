package com.hawatri.pinit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hawatri.pinit.util.formatAlarmText

/**
 * Renders one chip per reminder. Each chip's trailing × cancels the alarm.
 * Issue #3: a note can hold multiple reminders, so this replaced the single
 * AssistChip that the edit screens used to show.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemindersChipRow(
    reminders: List<Long>,
    onRemove: (Long) -> Unit,
    onEditClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (reminders.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        reminders.sorted().forEach { time ->
            AssistChip(
                onClick = onEditClick,
                label = { Text(formatAlarmText(time)) },
                leadingIcon = {
                    Icon(Icons.Filled.Notifications, "Alarm", modifier = Modifier.size(16.dp))
                },
                trailingIcon = {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove reminder",
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { onRemove(time) }
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                border = null
            )
        }
    }
}
