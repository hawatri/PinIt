package com.hawatri.pinit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.hawatri.pinit.backup.BackupSyncManager
import com.hawatri.pinit.backup.GoogleAuthManager
import com.hawatri.pinit.data.AppPreferences
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopSearchBar(
    modifier: Modifier = Modifier,
    onArchiveClick: () -> Unit,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onSortClick: (() -> Unit)? = null,
    onSettingsClick: () -> Unit = {},
    onSignInClick: () -> Unit = {}
) {
    var profileMenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Recompute account on every recomposition so the avatar updates immediately
    // after the user signs in or out without requiring a screen reload.
    val account = remember(profileMenuExpanded) { GoogleAuthManager.currentAccount(context) }
    val photoUrl = account?.photoUrl?.toString()
    val displayName = account?.displayName ?: AppPreferences.getUserName(context)
    val email = account?.email ?: AppPreferences.getUserEmail(context)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = "Search",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp)
        )

        TextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                Text(
                    text = "Search items",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 16.sp
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
            singleLine = true
        )

        if (searchQuery.isNotEmpty()) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable { onSearchQueryChange("") }
            )
        }

        if (onSortClick != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Sort,
                contentDescription = "Sort",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp).clickable { onSortClick() }
            )
        }

        Icon(
            imageVector = Icons.Filled.Archive,
            contentDescription = "Archived Notes",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 12.dp)
                .clickable { onArchiveClick() }
        )

        Box {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable { profileMenuExpanded = true },
                contentAlignment = Alignment.Center
            ) {
                if (!photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = "Profile",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = profileMenuExpanded,
                onDismissRequest = { profileMenuExpanded = false }
            ) {
                if (account != null) {
                    // Account header — name + email, not clickable
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(
                            displayName ?: "PinIt user",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (!email.isNullOrBlank()) {
                            Text(
                                email,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Account") },
                        leadingIcon = { Icon(Icons.Filled.Person, null) },
                        onClick = { profileMenuExpanded = false; onSignInClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Back up to Drive") },
                        leadingIcon = { Icon(Icons.Filled.CloudUpload, null) },
                        onClick = {
                            profileMenuExpanded = false
                            scope.launch { BackupSyncManager.backupNow(context) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                        onClick = { profileMenuExpanded = false; onSettingsClick() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Sign out") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, null) },
                        onClick = {
                            profileMenuExpanded = false
                            scope.launch {
                                GoogleAuthManager.signOut(context)
                                AppPreferences.signOut(context)
                                BackupSyncManager.reset()
                            }
                        }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                        onClick = { profileMenuExpanded = false; onSettingsClick() }
                    )
                    DropdownMenuItem(
                        text = { Text("Sign in") },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Login, null) },
                        onClick = { profileMenuExpanded = false; onSignInClick() }
                    )
                }
            }
        }
    }
}
