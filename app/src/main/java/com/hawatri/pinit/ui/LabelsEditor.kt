package com.hawatri.pinit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LabelsEditorSheet(
    currentLabels: List<String>,
    allExistingLabels: List<String>,
    onLabelsChange: (List<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val suggestions = remember(allExistingLabels, currentLabels) {
        allExistingLabels.filter { it !in currentLabels }.distinct()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            Text("Labels", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

            // Current labels as removable chips
            if (currentLabels.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    currentLabels.forEach { label ->
                        InputChip(
                            selected = true,
                            onClick = {},
                            label = { Text(label, fontSize = 13.sp) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onLabelsChange(currentLabels - label) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(Icons.Filled.Close, "Remove", modifier = Modifier.size(14.dp))
                                }
                            }
                        )
                    }
                }
            }

            // Input to add new label
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Add new label") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val trimmed = inputText.trim()
                        if (trimmed.isNotBlank() && trimmed !in currentLabels) {
                            onLabelsChange(currentLabels + trimmed)
                        }
                        inputText = ""
                    }
                ) {
                    Icon(Icons.Filled.Add, "Add Label", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // Suggestions (labels from other notes not already applied)
            if (suggestions.isNotEmpty()) {
                Text(
                    "Suggestions",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestions) { label ->
                        SuggestionChip(
                            onClick = { onLabelsChange(currentLabels + label) },
                            label = { Text(label, fontSize = 13.sp) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Done") }
        }
    }
}
