package com.hawatri.pinit.ui

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewNoteScreen(onNavigateBack: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Handle Pin */ }) {
                        Icon(Icons.Filled.PushPin, contentDescription = "Pin", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { /* Handle Reminder */ }) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Reminder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { /* Handle Label */ }) {
                        Icon(Icons.Filled.Label, contentDescription = "Label", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { /* Handle Save */ }) {
                        Icon(Icons.Filled.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imePadding(), // Pushes content up when keyboard appears
            verticalArrangement = Arrangement.Bottom
        ) {

            // Spacer to push everything to the bottom
            Spacer(modifier = Modifier.weight(1f))

            // Input Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "*Mandatory field",
                        color = Color(0xFFD32F2F), // Red color from screenshot
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    )

                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Title*", fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        textStyle = TextStyle(fontSize = 24.sp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    TextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        placeholder = { Text("Text*", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Formatting Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color.Transparent),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormatIcon(icon = Icons.Filled.FormatBold, description = "Bold")
                FormatIcon(icon = Icons.Filled.FormatItalic, description = "Italic")
                FormatIcon(icon = Icons.Filled.StrikethroughS, description = "Strikethrough")
                FormatIcon(icon = Icons.Filled.Title, description = "Heading")
                FormatIcon(icon = Icons.Filled.FormatListBulleted, description = "Bullet List")
                FormatIcon(icon = Icons.Filled.Checklist, description = "Checklist")
            }
        }
    }
}

@Composable
fun FormatIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}