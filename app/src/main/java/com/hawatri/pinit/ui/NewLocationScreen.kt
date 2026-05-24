package com.hawatri.pinit.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.preference.PreferenceManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteType
import com.hawatri.pinit.util.NotificationHelper
import com.hawatri.pinit.viewmodel.PinItViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
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
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var currentNoteId by remember(noteId) { mutableStateOf(noteId ?: UUID.randomUUID().toString()) }

    val notesList by viewModel.notes.collectAsState()
    var isInitialized by remember { mutableStateOf(false) }

    // Initialize osmdroid config once
    LaunchedEffect(Unit) {
        Configuration.getInstance().load(
            context.applicationContext,
            PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        )
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(20.5937, 78.9629)) // India default
        }
    }

    fun updateMarker(point: GeoPoint, zoom: Double? = null) {
        mapView.overlays.removeAll { it is Marker }
        val marker = Marker(mapView).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = locationName.ifBlank { "Selected Location" }
        }
        mapView.overlays.add(marker)
        mapView.controller.animateTo(point)
        if (zoom != null) mapView.controller.setZoom(zoom)
        mapView.invalidate()
    }

    fun reverseGeocode(latVal: Double, lngVal: Double) {
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val addresses = geocoder.getFromLocation(latVal, lngVal, 1)
                    if (!addresses.isNullOrEmpty()) {
                        val addr = addresses[0]
                        val full = buildString {
                            if (!addr.featureName.isNullOrBlank() && addr.featureName != addr.thoroughfare) append(addr.featureName)
                            if (!addr.thoroughfare.isNullOrBlank()) { if (isNotEmpty()) append(", "); append(addr.thoroughfare) }
                            if (!addr.subLocality.isNullOrBlank()) { if (isNotEmpty()) append(", "); append(addr.subLocality) }
                            if (!addr.locality.isNullOrBlank()) { if (isNotEmpty()) append(", "); append(addr.locality) }
                            if (!addr.adminArea.isNullOrBlank()) { if (isNotEmpty()) append(", "); append(addr.adminArea) }
                            if (!addr.postalCode.isNullOrBlank()) { if (isNotEmpty()) append(" "); append(addr.postalCode) }
                            if (!addr.countryName.isNullOrBlank()) { if (isNotEmpty()) append(", "); append(addr.countryName) }
                        }
                        locationAddress = full.ifBlank { "$latVal, $lngVal" }
                        if (locationName.isBlank()) {
                            locationName = addr.locality ?: addr.subAdminArea ?: addr.featureName ?: "Location"
                        }
                    } else {
                        locationAddress = "$latVal, $lngVal"
                        if (locationName.isBlank()) locationName = "Location"
                    }
                } catch (e: Exception) {
                    locationAddress = "$latVal, $lngVal"
                    if (locationName.isBlank()) locationName = "Location"
                }
            }
        }
    }

    // Tap-to-place
    LaunchedEffect(Unit) {
        val eventsOverlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                if (p != null) {
                    lat = p.latitude
                    lng = p.longitude
                    updateMarker(p)
                    reverseGeocode(p.latitude, p.longitude)
                }
                return true
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
        mapView.overlays.add(0, eventsOverlay)
    }

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
                    if (data.lat != null && data.lng != null) {
                        val gp = GeoPoint(data.lat, data.lng)
                        updateMarker(gp, 16.0)
                    }
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
                    val gp = GeoPoint(location.latitude, location.longitude)
                    withContext(Dispatchers.Main) { updateMarker(gp, 17.0) }
                    reverseGeocode(location.latitude, location.longitude)
                }
            }
            isLoadingLocation = false
        }
    }

    fun runSearch() {
        val query = searchQuery.trim()
        if (query.isBlank()) return
        isSearching = true
        scope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val results = geocoder.getFromLocationName(query, 1)
                    if (!results.isNullOrEmpty()) {
                        val r = results[0]
                        lat = r.latitude
                        lng = r.longitude
                        val gp = GeoPoint(r.latitude, r.longitude)
                        withContext(Dispatchers.Main) { updateMarker(gp, 16.0) }
                        val full = buildString {
                            for (i in 0..r.maxAddressLineIndex) {
                                if (isNotEmpty()) append(", ")
                                append(r.getAddressLine(i))
                            }
                        }
                        locationAddress = full.ifBlank { query }
                        if (locationName.isBlank()) locationName = r.featureName ?: r.locality ?: query
                    }
                } catch (e: Exception) { /* ignore */ }
            }
            isSearching = false
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
                        val text = gson.toJson(LocationNoteData(locationName, locationAddress, lat, lng))
                        if (isPinned) notificationHelper.pinNoteToNotification(savedId, locationName.ifBlank { "Location" }, text, isList = false, noteType = NoteType.LOCATION)
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
        Box(modifier = Modifier.fillMaxSize().padding(top = paddingValues.calculateTopPadding())) {
            // Map
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { /* state-driven updates handled imperatively above */ }
            )

            // Search bar at top
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).align(Alignment.TopCenter),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search a place", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { runSearch() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                        modifier = Modifier.weight(1f)
                    )
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                    } else if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Close, "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // My-Location FAB
            FloatingActionButton(
                onClick = { fetchLocation() },
                modifier = Modifier.align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = paddingValues.calculateBottomPadding() + 180.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                if (isLoadingLocation) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.MyLocation, "Get My Location", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            // Bottom info card with name/address + Navigate
            Card(
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .padding(16.dp).padding(bottom = paddingValues.calculateBottomPadding()),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = locationName,
                            onValueChange = { locationName = it },
                            placeholder = { Text("Location name", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)) },
                            colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
                            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Medium),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (locationAddress.isNotBlank()) {
                        Text(
                            locationAddress,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(start = 30.dp, end = 8.dp, top = 2.dp, bottom = 8.dp)
                        )
                    } else {
                        Text(
                            "Tap on the map or use the location button",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 30.dp, top = 2.dp, bottom = 8.dp)
                        )
                    }
                    if (lat != null && lng != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(start = 30.dp, end = 8.dp, top = 4.dp)
                        ) {
                            Text(
                                "${String.format("%.5f", lat)}, ${String.format("%.5f", lng)}",
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    val uri = Uri.parse("geo:${lat},${lng}?q=${lat},${lng}(${Uri.encode(locationName.ifBlank { "Location" })})")
                                    val intent = Intent(Intent.ACTION_VIEW, uri).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    try { context.startActivity(intent) } catch (e: Exception) { }
                                }
                            ) {
                                Icon(Icons.Filled.Navigation, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Navigate", fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }
}
