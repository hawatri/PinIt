package com.hawatri.pinit.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.viewmodel.PinItViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID

data class LocationNoteData(val name: String, val address: String, val lat: Double?, val lng: Double?)

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewLocationScreen(
    noteId: String? = null,
    onNavigateBack: () -> Unit,
    viewModel: PinItViewModel
) {
    val context = LocalContext.current
    val notificationHelper = remember(context) { NotificationHelper(context) }
    val gson = remember { Gson() }
    val scope = rememberCoroutineScope()

    var locationName by remember { mutableStateOf("") }
    var locationAddress by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var isPinned by remember { mutableStateOf(false) }
    var isLoadingLocation by remember { mutableStateOf(false) }
    var currentNoteId by remember(noteId) { mutableStateOf(noteId ?: UUID.randomUUID().toString()) }

    val notesList by viewModel.notes.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(notesList, noteId) {
        if (noteId != null && !isInitialized && notesList.isNotEmpty()) {
            val existing = notesList.find { it.id == noteId }
            if (existing != null) {
                try {
                    val data = gson.fromJson(existing.text, LocationNoteData::class.java)
                    locationName = data.name
                    locationAddress = data.address
                    lat = data.lat
                    lng = data.lng
                } catch (e: Exception) { locationName = existing.title }
                isPinned = existing.isPinned
                isInitialized = true
            }
        }
    }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        hasLocationPermission = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    fun fetchLocation() {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            return
        }
        isLoadingLocation = true
        scope.launch {
            withContext(Dispatchers.IO) {
                val locationManager = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                if (location != null) {
                    lat = location.latitude
                    lng = location.longitude
                    try {
                        val geocoder = Geocoder(context, Locale.getDefault())
                        @Suppress("DEPRECATION")
                        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val addr = addresses[0]
                            locationAddress = buildString {
                                if (!addr.thoroughfare.isNullOrBlank()) append(addr.thoroughfare)
                                if (!addr.locality.isNullOrBlank()) { if (isNotEmpty()) append(", "); append(addr.locality) }
                                if (!addr.countryName.isNullOrBlank()) { if (isNotEmpty()) append(", "); append(addr.countryName) }
                            }
                            if (locationName.isBlank()) locationName = addr.locality ?: "My Location"
                        }
                    } catch (e: Exception) {
                        locationAddress = "${location.latitude}, ${location.longitude}"
                        if (locationName.isBlank()) locationName = "My Location"
                    }
                }
            }
            isLoadingLocation = false
        }
    }

    fun save(pinOverride: Boolean = isPinned): String {
        val data = LocationNoteData(locationName, locationAddress, lat, lng)
        val note = Note(
            id = currentNoteId,
            title = locationName.ifBlank { "Location" },
            text = gson.toJson(data),
            formatRanges = emptyList(),
            isPinned = pinOverride,
            isList = false,
            noteType = NoteType.LOCATION
        )
        val existing = notesList.find { it.id == currentNoteId }
        if (existing != null) viewModel.updateNote(note) else viewModel.addNote(note)
        return currentNoteId
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
                },
                actions = {
                    IconButton(onClick = {
                        isPinned = !isPinned
                        val savedId = save(isPinned)
                        if (isPinned) notificationHelper.pinNoteToNotification(savedId, locationName.ifBlank { "Location" }, locationAddress)
                        else notificationHelper.unpinNoteFromNotification(savedId)
                    }) {
                        Icon(if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, "Pin",
                            tint = if (isPinned) MaterialTheme.colorScheme.primary else Color.White)
                    }
                    IconButton(onClick = { if (locationName.isNotBlank() || lat != null) { save(); onNavigateBack() } }) {
                        Icon(Icons.Filled.Check, "Save", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF4A525E))) {
            Icon(
                imageVector = Icons.Filled.LocationOn,
                contentDescription = "Center Pin",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp).offset(y = (-18).dp)
            )

            // Get My Location FAB
            FloatingActionButton(
                onClick = { fetchLocation() },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = paddingValues.calculateBottomPadding() + 220.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (isLoadingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.MyLocation, "Get My Location", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .padding(16.dp).padding(bottom = paddingValues.calculateBottomPadding()),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    TextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        placeholder = { Text("Location name", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (locationAddress.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                            Icon(Icons.Filled.LocationOn, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(locationAddress, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 16.sp)
                        }
                    } else {
                        Text(
                            "Tap  to get your current location",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                    if (lat != null && lng != null) {
                        Text(
                            "${String.format("%.5f", lat)}, ${String.format("%.5f", lng)}",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
