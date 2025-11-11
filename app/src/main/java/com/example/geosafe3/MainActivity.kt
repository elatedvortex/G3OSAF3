package com.example.geosafe3

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.IOException
import java.util.UUID

data class MarkerData(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    val snippet: String,
    val reportedBy: String,
    val crimeType: String = "OTHER",
    val status: CrimeStatus = CrimeStatus.PENDING,
    val approvers: List<String> = emptyList()
)

enum class CrimeStatus {
    PENDING,
    APPROVED
}

enum class CrimeType(val icon: Int) {
    THEFT(R.drawable.ic_robber),
    HIT_AND_RUN(R.drawable.ic_car),
    MURDER(R.drawable.ic_knife),
    OTHER(R.drawable.ic_warning)
}

private class CustomInfoWindow(
    mapView: MapView,
    private val markersData: MutableList<MarkerData>,
    private val username: String,
    private val isAdmin: Boolean,
    private val onSave: () -> Unit
) : InfoWindow(R.layout.custom_bubble, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as Marker
        val data = markersData.find { it.id == marker.id } ?: return

        val titleView = mView.findViewById<TextView>(R.id.bubble_title)
        val descriptionView = mView.findViewById<TextView>(R.id.bubble_description)
        val subDescriptionView = mView.findViewById<TextView>(R.id.bubble_subdescription)
        val closeButton = mView.findViewById<ImageButton>(R.id.bubble_close)
        val approveButton = mView.findViewById<Button>(R.id.bubble_approve)

        titleView.text = marker.title
        descriptionView.text = marker.snippet
        subDescriptionView.text = "Reported by: ${data.reportedBy}\nApprovals: ${data.approvers.joinToString()}"

        closeButton.setOnClickListener {
            marker.closeInfoWindow()
        }

        if (data.status == CrimeStatus.PENDING && !data.approvers.contains(username) && data.reportedBy != username) {
            approveButton.visibility = View.VISIBLE
            approveButton.setOnClickListener {
                val index = markersData.indexOf(data)
                val updatedMarker = data.copy(
                    approvers = data.approvers + username,
                    status = if (isAdmin || data.approvers.size >= 2) CrimeStatus.APPROVED else CrimeStatus.PENDING
                )
                markersData[index] = updatedMarker
                onSave()
                marker.closeInfoWindow()
            }
        } else {
            approveButton.visibility = View.GONE
        }
    }

    override fun onClose() {}
}

private class MyMarker(mapView: MapView) : Marker(mapView) {
    override fun onLongPress(event: MotionEvent?, mapView: MapView): Boolean {
        if (isInfoWindowShown) {
            closeInfoWindow()
        } else {
            showInfoWindow()
        }
        return true
    }
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            setupMap()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osmdroid", MODE_PRIVATE))

        if (hasLocationPermission()) {
            setupMap()
        } else {
            requestLocationPermission()
        }
    }

    private fun setupMap() {
        setContent {
            App()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        requestPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }
}

@Composable
fun App() {
    var username by remember { mutableStateOf<String?>(null) }
    var isAdmin by remember { mutableStateOf(false) }

    if (username == null) {
        LoginScreen(
            onLoginAsUser = { name ->
                username = name
                isAdmin = false
            },
            onLoginAsAdmin = {
                username = "Admin"
                isAdmin = true
            }
        )
    } else {
        val mapView = rememberMapViewWithLifecycle()
        MapScreen(mapView, username!!, isAdmin) {
            username = null
        }
    }
}

@Composable
fun LoginScreen(onLoginAsUser: (String) -> Unit, onLoginAsAdmin: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Username") },
            modifier = Modifier.padding(bottom = 16.dp)
        )
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password (for admin)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = { onLoginAsUser(name) }, enabled = name.isNotBlank()) {
                Text("Login as User")
            }
            Button(
                onClick = { onLoginAsAdmin() },
                enabled = name == "admin" && password == "admin"
            ) {
                Text("Login as Admin")
            }
        }
    }
}

@Composable
fun MapScreen(mapView: MapView, username: String, isAdmin: Boolean, onLogout: () -> Unit) {
    val context = LocalContext.current
    val fusedLocationProviderClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val markersData = remember { mutableStateListOf<MarkerData>() }
    var showAddMarkerDialog by remember { mutableStateOf(false) }
    var showReportsDialog by remember { mutableStateOf(false) }
    var showHeatmap by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("All") + CrimeType.values().map { it.name.replace('_', ' ') }
    var visibleCrimes by remember { mutableStateOf(listOf<MarkerData>()) }

    LaunchedEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("markers", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPrefs.getString("marker_list", null)
        if (json != null) {
            val type = object : TypeToken<List<MarkerData>>() {}.type
            val loadedData: List<MarkerData> = gson.fromJson(json, type)
            markersData.addAll(loadedData)
        }
    }

    if (showAddMarkerDialog) {
        AddMarkerDialog(mapView, markersData, username, isAdmin) { showAddMarkerDialog = false }
    }

    if (showReportsDialog) {
        ReportsDialog(markersData, username, isAdmin, {
            markersData.remove(it)
            saveMarkersData(context, markersData)
        }) { showReportsDialog = false }
    }

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = selectedTabIndex) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        text = { Text(title) },
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    mapView.apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(context), this)
                        locationOverlay.enableMyLocation()
                        overlays.add(locationOverlay)

                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                visibleCrimes = markersData.filter {
                                    boundingBox.contains(GeoPoint(it.latitude, it.longitude))
                                }
                                return false
                            }

                            override fun onZoom(event: ZoomEvent?): Boolean {
                                visibleCrimes = markersData.filter {
                                    boundingBox.contains(GeoPoint(it.latitude, it.longitude))
                                }
                                return false
                            }
                        })

                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                                location?.let {
                                    val geoPoint = GeoPoint(it.latitude, it.longitude)
                                    controller.animateTo(geoPoint)
                                    controller.setZoom(15.0)
                                }
                            }
                        }
                    }
                },
                update = { map ->
                    val crimeTypeToFilter = if (selectedTabIndex > 0) CrimeType.values()[selectedTabIndex - 1].name else null
                    val filteredMarkersData = markersData.filter { crimeTypeToFilter == null || it.crimeType == crimeTypeToFilter }

                    val myLocationOverlay = map.overlays.find { it is MyLocationNewOverlay }
                    map.overlays.clear()
                    myLocationOverlay?.let { map.overlays.add(it) }

                    filteredMarkersData.forEach { data ->
                        val crimeType = try {
                            CrimeType.valueOf(data.crimeType)
                        } catch (e: IllegalArgumentException) {
                            CrimeType.OTHER
                        }
                        val icon = if (data.status == CrimeStatus.PENDING) R.drawable.ic_warning_yellow else crimeType.icon
                        val newMarker = MyMarker(map).apply {
                            this.id = data.id
                            position = GeoPoint(data.latitude, data.longitude)
                            title = data.title
                            snippet = data.snippet
                            subDescription = "Reported by: ${data.reportedBy}\nApprovals: ${data.approvers.joinToString()}"
                            this.icon = ContextCompat.getDrawable(context, icon)
                            infoWindow = CustomInfoWindow(map, markersData, username, isAdmin) { saveMarkersData(context, markersData) }
                        }
                        map.overlays.add(newMarker)
                    }

                    if (showHeatmap) {
                        filteredMarkersData.forEach { data ->
                            val nearbyCrimes = markersData.count {
                                val geoPoint1 = GeoPoint(it.latitude, it.longitude)
                                val geoPoint2 = GeoPoint(data.latitude, data.longitude)
                                geoPoint1.distanceToAsDouble(geoPoint2) <= 2000
                            }

                            val circle = Polygon().apply {
                                points = Polygon.pointsAsCircle(GeoPoint(data.latitude, data.longitude), 2000.0)
                                val color = when {
                                    nearbyCrimes <= 1 -> Color.argb(100, 0, 0, 255)
                                    nearbyCrimes in 2..5 -> Color.argb(100, 255, 255, 0)
                                    else -> Color.argb(100, 255, 0, 0)
                                }
                                fillPaint.color = color
                                outlinePaint.strokeWidth = 0f
                            }
                            map.overlays.add(circle)
                        }
                    }

                    map.invalidate()
                }
            )

            Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = "Crosshair",
                modifier = Modifier.align(Alignment.Center)
            )

            LazyRow(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp)
                    .background(ComposeColor.LightGray.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
            ) {
                items(visibleCrimes) { crime ->
                    Text(
                        text = crime.title,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            FloatingActionButton(
                onClick = { showReportsDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Show reports")
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(onClick = onLogout) {
                    Icon(Icons.Default.Logout, contentDescription = "Logout")
                }
                IconButton(onClick = { showHeatmap = !showHeatmap }) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Toggle Heatmap",
                        tint = if (showHeatmap) ComposeColor.Red else ComposeColor.Gray
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
                                location?.let {
                                    val geoPoint = GeoPoint(it.latitude, it.longitude)
                                    mapView.controller.animateTo(geoPoint)
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Go to my location")
                }

                FloatingActionButton(
                    onClick = { showAddMarkerDialog = true }
                ) {
                    Icon(Icons.Default.AddLocation, contentDescription = "Add a marker")
                }
            }
        }
    }
}

@Composable
fun AddMarkerDialog(mapView: MapView, markersData: MutableList<MarkerData>, username: String, isAdmin: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var crimeName by remember { mutableStateOf("") }
    var crimeDescription by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedCrimeType by remember { mutableStateOf(CrimeType.OTHER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report a crime") },
        text = {
            Column {
                TextField(
                    value = crimeName,
                    onValueChange = { crimeName = it },
                    label = { Text("Crime Name") }
                )
                TextField(
                    value = crimeDescription,
                    onValueChange = { crimeDescription = it },
                    label = { Text("Crime Description") }
                )
                Box {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(selectedCrimeType.name, modifier = Modifier.weight(1f))
                        Icon(Icons.Default.ArrowDropDown, contentDescription = "Select crime type")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        CrimeType.values().forEach { crimeType ->
                            DropdownMenuItem(text = { Text(crimeType.name) }, onClick = { 
                                selectedCrimeType = crimeType
                                expanded = false
                            })
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newMarkerData = MarkerData(
                        id = UUID.randomUUID().toString(),
                        latitude = mapView.mapCenter.latitude,
                        longitude = mapView.mapCenter.longitude,
                        title = crimeName,
                        snippet = crimeDescription,
                        reportedBy = username,
                        crimeType = selectedCrimeType.name,
                        status = if (isAdmin) CrimeStatus.APPROVED else CrimeStatus.PENDING,
                        approvers = if (isAdmin) listOf(username) else emptyList()
                    )
                    markersData.add(newMarkerData)

                    saveMarkersData(context, markersData)
                    onDismiss()
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ReportsDialog(markersData: List<MarkerData>, username: String, isAdmin: Boolean, onDeleteMarker: (MarkerData) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val geocoder = remember { Geocoder(context) }
    val userReports = if (isAdmin) markersData else markersData.filter { it.reportedBy == username }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isAdmin) "All Crime Reports" else "My Crime Reports") },
        text = {
            LazyColumn {
                items(userReports) { data ->
                    var address by remember { mutableStateOf("Loading address...") }

                    LaunchedEffect(data) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            geocoder.getFromLocation(data.latitude, data.longitude, 1) { addresses ->
                                address = addresses.firstOrNull()?.getAddressLine(0) ?: "No address found"
                            }
                        } else {
                            try {
                                @Suppress("DEPRECATION")
                                val addresses = geocoder.getFromLocation(data.latitude, data.longitude, 1)
                                address = addresses?.firstOrNull()?.getAddressLine(0) ?: "No address found"
                            }
                            catch (e: IOException) {
                                address = "Error getting address"
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.padding(16.dp).weight(1f)) {
                            Text("Crime: ${data.title}")
                            Text("Address: $address")
                            Text("Reported by: ${data.reportedBy}")
                        }
                        if (isAdmin) {
                            IconButton(onClick = { onDeleteMarker(data) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete report")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

fun saveMarkersData(context: Context, markersData: List<MarkerData>) {
    val sharedPrefs = context.getSharedPreferences("markers", Context.MODE_PRIVATE)
    val gson = Gson()
    val json = gson.toJson(markersData)
    sharedPrefs.edit().putString("marker_list", json).apply()
}

@Composable
fun rememberMapViewWithLifecycle(): MapView {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context)
    }
    return mapView
}
