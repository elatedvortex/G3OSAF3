@file:OptIn(ExperimentalMaterial3Api::class)
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
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
import com.example.geosafe3.model.toMarkerData

// Retrofit api + models
import com.example.geosafe3.api.RetrofitClient
import com.example.geosafe3.api.ReportRequest
import com.example.geosafe3.model.MarkerReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


// app UI model(s)
import com.example.geosafe3.model.MarkerData
import com.example.geosafe3.model.CrimeType

private class CustomInfoWindow(
    mapView: MapView,
    private val markersData: MutableList<MarkerData>,
    private val username: String,
    private val isAdmin: Boolean,
    private val onSave: () -> Unit,
    private val onApprove: (String) -> Unit
) : InfoWindow(R.layout.custom_bubble, mapView) {

    override fun onOpen(item: Any?) {
        val marker = item as? Marker ?: return

        // Find the marker data by matching ID exactly
        val data = markersData.find { it.id == marker.id }

        if (data == null) {
            println("WARNING: Could not find marker data for marker ID: ${marker.id}")
            return
        }

        println("Opening info window for marker: ${data.id}, title: ${data.title}")

        val titleView = mView.findViewById<TextView>(R.id.bubble_title)
        val descriptionView = mView.findViewById<TextView>(R.id.bubble_description)
        val subDescriptionView = mView.findViewById<TextView>(R.id.bubble_subdescription)
        val closeButton = mView.findViewById<ImageButton>(R.id.bubble_close)
        val approveButton = mView.findViewById<Button>(R.id.bubble_approve)

        titleView.text = marker.title
        descriptionView.text = marker.snippet

        val approversText = if (data.approvers.isEmpty()) "None" else data.approvers.joinToString(", ")
        subDescriptionView.text = "Reported by: ${data.reportedBy}\nStatus: ${data.status}\nApprovers: $approversText"

        closeButton.setOnClickListener {
            marker.closeInfoWindow()
        }

        // Show approve button only if:
        // 1. Report is PENDING
        // 2. User hasn't already approved
        // 3. User is not the reporter
        val canApprove = data.status == "PENDING" &&
                !data.approvers.contains(username) &&
                data.reportedBy != username

        if (canApprove) {
            approveButton.visibility = View.VISIBLE
            approveButton.text = if (isAdmin) "Approve (Admin)" else "Approve"
            approveButton.setOnClickListener {
                // Trigger remote approval first
                onApprove(data.id)
                marker.closeInfoWindow()
            }
        } else {
            approveButton.visibility = View.GONE
        }
    }

    override fun onClose() {}
}

private class MyMarker(mapView: MapView) : Marker(mapView) {

    // Handle regular tap/click
    override fun onMarkerClickDefault(marker: Marker?, mapView: MapView?): Boolean {
        if (isInfoWindowShown) {
            closeInfoWindow()
        } else {
            showInfoWindow()
        }
        return true
    }

    // Handle long press as well
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
    var showPassword by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // App Icon/Logo
            Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = "GeoSafe Logo",
                modifier = Modifier.size(80.dp),
                tint = ComposeColor(0xFF2196F3)
            )

            // App Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "GeoSafe",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
                    color = ComposeColor(0xFF2196F3)
                )
                Text(
                    text = "Community Crime Reporting",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    color = ComposeColor.Gray
                )
            }

            androidx.compose.material3.Card(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = ComposeColor.White
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Username Field
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Username") },
                        leadingIcon = {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.AccountCircle,
                                contentDescription = "Username"
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Password Field
                    androidx.compose.material3.OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (for admin)") },
                        leadingIcon = {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Lock,
                                contentDescription = "Password"
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword)
                                        androidx.compose.material.icons.Icons.Default.VisibilityOff
                                    else
                                        androidx.compose.material.icons.Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword)
                            androidx.compose.ui.text.input.VisualTransformation.None
                        else
                            PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    // Login as User Button
                    Button(
                        onClick = { onLoginAsUser(name) },
                        enabled = name.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = ComposeColor(0xFF2196F3)
                        )
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                        Text("Login as User")
                    }

                    // Login as Admin Button
                    androidx.compose.material3.OutlinedButton(
                        onClick = { onLoginAsAdmin() },
                        enabled = name == "admin" && password == "admin",
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = ComposeColor(0xFFFF5722)
                        )
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.AdminPanelSettings,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                        Text("Login as Admin")
                    }
                }
            }

            // Helper text
            Text(
                text = "Enter any username to login as a regular user\nUse admin/admin for administrator access",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = ComposeColor.Gray,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun MapScreen(mapView: MapView, username: String, isAdmin: Boolean, onLogout: () -> Unit) {
    val context = LocalContext.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
    val fusedLocationProviderClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    val markersData = remember { mutableStateListOf<MarkerData>() }
    val gson = remember { Gson() }
    val api = RetrofitClient.api

    // fetch reports periodically
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.getReports()
                }

                if (response.isSuccessful) {
                    val listFromServer = response.body() ?: emptyList()
                    markersData.clear()
                    markersData.addAll(listFromServer.map { it.toMarkerData() })
                    saveMarkersData(context, markersData)
                    errorMessage = null
                } else {
                    errorMessage = "Error: ${response.code()}"
                }

            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = e.message
            }

            kotlinx.coroutines.delay(5000)
        }
    }


    // --- suspend helpers to POST / approve / delete ---
    suspend fun postReportToServer(newMarker: MarkerData, username: String, isAdmin: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val request = ReportRequest(
                    latitude = newMarker.latitude,
                    longitude = newMarker.longitude,
                    title = newMarker.title,
                    snippet = newMarker.snippet,
                    crimeType = newMarker.crimeType
                )
                val userInfoJson = Gson().toJson(mapOf("username" to username, "isAdmin" to isAdmin.toString()))

                // Debug logging
                println("POST /report - Request: $request")
                println("POST /report - User Info: $userInfoJson")

                val response = api.createReport(request, userInfoJson)

                println("POST /report - Response Code: ${response.code()}")
                if (!response.isSuccessful) {
                    println("POST /report - Error Body: ${response.errorBody()?.string()}")
                }

                if (response.isSuccessful) {
                    response.body()?.let { created ->
                        withContext(Dispatchers.Main) {
                            markersData.add(created.toMarkerData())
                            saveMarkersData(context, markersData)
                        }
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }


    suspend fun approveReportOnServer(reportId: String, username: String, isAdmin: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userInfoJson = Gson().toJson(mapOf("username" to username, "isAdmin" to isAdmin.toString()))

                println("POST /report/$reportId/approve - User Info: $userInfoJson")

                val response = api.approveReport(reportId, userInfoJson)

                println("POST /report/$reportId/approve - Response Code: ${response.code()}")
                if (!response.isSuccessful) {
                    println("POST /report/$reportId/approve - Error: ${response.errorBody()?.string()}")
                }

                if (response.isSuccessful) {
                    response.body()?.let { updated ->
                        withContext(Dispatchers.Main) {
                            val idx = markersData.indexOfFirst { it.id == updated.id }
                            if (idx >= 0) {
                                markersData[idx] = updated.toMarkerData()
                                saveMarkersData(context, markersData)
                                println("Successfully updated marker $reportId - Status: ${updated.status}, Approvers: ${updated.approvers}")
                            }
                        }
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                println("Error approving report: ${e.message}")
                false
            }
        }
    }

    suspend fun deleteReportOnServer(reportId: String, username: String, isAdmin: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val userInfoJson = Gson().toJson(mapOf("username" to username, "isAdmin" to isAdmin.toString()))
                val response = api.deleteReport(reportId, userInfoJson)

                if (response.isSuccessful) {
                    withContext(Dispatchers.Main) {
                        val idx = markersData.indexOfFirst { it.id == reportId }
                        if (idx >= 0) {
                            markersData.removeAt(idx)
                            saveMarkersData(context, markersData)
                        }
                    }
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }


    // UI: show error or success message if any (moved to map overlay)

    var showAddMarkerDialog by remember { mutableStateOf(false) }
    var showReportsDialog by remember { mutableStateOf(false) }
    var showHeatmap by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var mapUpdateTrigger by remember { mutableStateOf(0) }  // Add trigger to force map updates
    val tabs = listOf("All") + CrimeType.values().map { it.name.replace('_', ' ') }

    LaunchedEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("markers", Context.MODE_PRIVATE)
        val gson2 = Gson()
        val json = sharedPrefs.getString("marker_list", null)
        if (json != null) {
            val type = object : TypeToken<List<MarkerData>>() {}.type
            val loadedData: List<MarkerData> = gson2.fromJson(json, type)
            markersData.addAll(loadedData)
        }
    }

    if (showAddMarkerDialog) {
        AddMarkerDialog(
            mapView = mapView,
            markersData = markersData,
            username = username,
            isAdmin = isAdmin,
            onDismiss = { showAddMarkerDialog = false },
            onPost = { newMarker ->
                // Call server API to post
                CoroutineScope(Dispatchers.Main).launch {
                    postReportToServer(newMarker, username, isAdmin)
                }
            }
        )
    }

    if (showReportsDialog) {
        ReportsDialog(markersData, username, isAdmin, { marker ->
            // admin delete: call server and local remove
            if (isAdmin) {
                // launch a coroutine to delete remotely
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    val ok = deleteReportOnServer(marker.id, username, isAdmin)
                    if (!ok) {
                        // fallback: remove locally but inform user (you can improve UX)
                        markersData.remove(marker)
                        saveMarkersData(context, markersData)
                    }
                }
            } else {
                // regular user: just remove locally (or do nothing)
                markersData.remove(marker)
                saveMarkersData(context, markersData)
            }
        }) { showReportsDialog = false }
    }

    Scaffold(
        topBar = {
            Column {
                // Top App Bar with user info
                androidx.compose.material3.TopAppBar(
                    title = {
                        Column {
                            Text(
                                "GeoSafe",
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                            Text(
                                "Logged in as: $username${if (isAdmin) " (Admin)" else ""}",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = ComposeColor.Gray
                            )
                        }
                    },
                    actions = {
                        // Heatmap toggle
                        IconButton(
                            onClick = { showHeatmap = !showHeatmap }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = "Toggle Heatmap",
                                tint = if (showHeatmap) ComposeColor(0xFFFF5722) else ComposeColor.Gray
                            )
                        }
                        // Logout button
                        IconButton(onClick = onLogout) {
                            Icon(
                                Icons.Default.Logout,
                                contentDescription = "Logout",
                                tint = ComposeColor(0xFF2196F3)
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = ComposeColor.White,
                        titleContentColor = ComposeColor(0xFF2196F3)
                    )
                )

                // Tab Row for filtering
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = ComposeColor.White,
                    contentColor = ComposeColor(0xFF2196F3)
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            text = {
                                Text(
                                    title,
                                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge
                                )
                            },
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            selectedContentColor = ComposeColor(0xFF2196F3),
                            unselectedContentColor = ComposeColor.Gray
                        )
                    }
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
                    // Use mapUpdateTrigger to force recomposition when it changes
                    // We don't need to use the value, just reference it
                    mapUpdateTrigger.let { }

                    val crimeTypeToFilter = if (selectedTabIndex > 0) CrimeType.values()[selectedTabIndex - 1].name else null
                    val filteredMarkersData = markersData.filter { crimeTypeToFilter == null || it.crimeType == crimeTypeToFilter }

                    // Close all info windows before rebuilding markers
                    map.overlays.filterIsInstance<Marker>().forEach { marker ->
                        marker.closeInfoWindow()
                    }

                    val myLocationOverlay = map.overlays.find { it is MyLocationNewOverlay }
                    map.overlays.clear()
                    myLocationOverlay?.let { map.overlays.add(it) }

                    filteredMarkersData.forEach { data ->
                        val crimeType = try {
                            CrimeType.valueOf(data.crimeType)
                        } catch (e: IllegalArgumentException) {
                            CrimeType.OTHER
                        }
                        val icon = if (data.status == "PENDING") R.drawable.ic_warning_yellow else crimeType.icon
                        val newMarker = MyMarker(map).apply {
                            this.id = data.id
                            position = GeoPoint(data.latitude, data.longitude)
                            title = data.title
                            snippet = data.snippet
                            subDescription = "Reported by: ${data.reportedBy}\nApprovals: ${data.approvers.joinToString()}"
                            this.icon = ContextCompat.getDrawable(context, icon)
                            // wire infoWindow with approval callback
                            infoWindow = CustomInfoWindow(
                                map,
                                markersData,
                                username,
                                isAdmin,
                                { saveMarkersData(context, markersData) }
                            ) { reportId ->
                                // When approve is clicked, call the server
                                CoroutineScope(Dispatchers.Main).launch {
                                    val success = approveReportOnServer(reportId, username, isAdmin)
                                    if (success) {
                                        successMessage = "Report approved successfully!"
                                        // Force map refresh after approval
                                        mapUpdateTrigger++
                                        kotlinx.coroutines.delay(100)
                                        map.invalidate()
                                    } else {
                                        errorMessage = "Failed to approve report"
                                    }
                                }
                            }
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

            // Status messages overlay
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                if (errorMessage != null) {
                    androidx.compose.material3.Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = ComposeColor(0xFFFFEBEE)
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Error,
                                contentDescription = null,
                                tint = ComposeColor(0xFFD32F2F),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage!!,
                                color = ComposeColor(0xFFD32F2F),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                if (successMessage != null) {
                    androidx.compose.material3.Card(
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = ComposeColor(0xFFE8F5E9)
                        ),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ComposeColor(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = successMessage!!,
                                color = ComposeColor(0xFF2E7D32),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    // Clear success message after 3 seconds
                    LaunchedEffect(successMessage) {
                        kotlinx.coroutines.delay(3000)
                        successMessage = null
                    }
                }
            }

            Icon(
                imageVector = Icons.Default.GpsFixed,
                contentDescription = "Crosshair",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
                tint = ComposeColor(0xFF2196F3)
            )

            // Bottom-left FAB - Show Reports
            FloatingActionButton(
                onClick = { showReportsDialog = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                containerColor = ComposeColor(0xFF2196F3)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = "Show reports",
                    tint = ComposeColor.White
                )
            }

            // Bottom-right FABs - Location and Add Report
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Go to my location button
                androidx.compose.material3.FloatingActionButton(
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
                    containerColor = ComposeColor.White
                ) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = "Go to my location",
                        tint = ComposeColor(0xFF2196F3)
                    )
                }

                // Add report button
                androidx.compose.material3.FloatingActionButton(
                    onClick = { showAddMarkerDialog = true },
                    containerColor = ComposeColor(0xFFFF5722)
                ) {
                    Icon(
                        Icons.Default.AddLocation,
                        contentDescription = "Add a marker",
                        tint = ComposeColor.White
                    )
                }
            }
        }
    }
}

@Composable
fun AddMarkerDialog(
    mapView: MapView,
    markersData: MutableList<MarkerData>,
    username: String,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onPost: (MarkerData) -> Unit
) {
    val context = LocalContext.current
    var crimeName by remember { mutableStateOf("") }
    var crimeDescription by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var selectedCrimeType by remember { mutableStateOf(CrimeType.OTHER) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AddLocation,
                    contentDescription = null,
                    tint = ComposeColor(0xFFFF5722),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Report a Crime",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = crimeName,
                    onValueChange = { crimeName = it },
                    label = { Text("Crime Title") },
                    placeholder = { Text("e.g., Bike Theft") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Title,
                            contentDescription = null
                        )
                    }
                )

                androidx.compose.material3.OutlinedTextField(
                    value = crimeDescription,
                    onValueChange = { crimeDescription = it },
                    label = { Text("Description") },
                    placeholder = { Text("Provide details about the incident...") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5,
                    leadingIcon = {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Description,
                            contentDescription = null
                        )
                    }
                )

                androidx.compose.material3.Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = ComposeColor(0xFFF5F5F5)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Crime Type",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            color = ComposeColor.Gray
                        )
                        Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { expanded = true }
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = when(selectedCrimeType) {
                                            CrimeType.THEFT -> androidx.compose.material.icons.Icons.Default.ShoppingBag
                                            CrimeType.HIT_AND_RUN -> androidx.compose.material.icons.Icons.Default.DirectionsCar
                                            CrimeType.MURDER -> androidx.compose.material.icons.Icons.Default.Warning
                                            CrimeType.OTHER -> androidx.compose.material.icons.Icons.Default.MoreHoriz
                                        },
                                        contentDescription = null,
                                        tint = ComposeColor(0xFF2196F3)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        selectedCrimeType.name.replace('_', ' '),
                                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                CrimeType.values().forEach { crimeType ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = when(crimeType) {
                                                        CrimeType.THEFT -> androidx.compose.material.icons.Icons.Default.ShoppingBag
                                                        CrimeType.HIT_AND_RUN -> androidx.compose.material.icons.Icons.Default.DirectionsCar
                                                        CrimeType.MURDER -> androidx.compose.material.icons.Icons.Default.Warning
                                                        CrimeType.OTHER -> androidx.compose.material.icons.Icons.Default.MoreHoriz
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(crimeType.name.replace('_', ' '))
                                            }
                                        },
                                        onClick = {
                                            selectedCrimeType = crimeType
                                            expanded = false
                                        }
                                    )
                                }
                            }
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
                        status = if (isAdmin) "APPROVED" else "PENDING",
                        approvers = if (isAdmin) listOf(username) else emptyList()
                    )

                    println("Adding new marker: ID=${newMarkerData.id}, title=${newMarkerData.title}")

                    // Don't add locally yet - wait for server response
                    // This prevents showing wrong marker immediately

                    // Call server API
                    onPost(newMarkerData)
                    onDismiss()
                },
                enabled = crimeName.isNotBlank() && crimeDescription.isNotBlank(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(0xFFFF5722)
                )
            ) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Submit Report")
            }
        },
        dismissButton = {
            androidx.compose.material3.OutlinedButton(onClick = onDismiss) {
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
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    tint = ComposeColor(0xFF2196F3),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        if (isAdmin) "All Crime Reports" else "My Crime Reports",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "${userReports.size} report${if (userReports.size != 1) "s" else ""}",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = ComposeColor.Gray
                    )
                }
            }
        },
        text = {
            if (userReports.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = ComposeColor.Gray
                    )
                    Spacer(modifier = Modifier.padding(8.dp))
                    Text(
                        "No reports found",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                        color = ComposeColor.Gray
                    )
                }
            } else {
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

                        androidx.compose.material3.Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = if (data.status == "APPROVED")
                                    ComposeColor(0xFFE8F5E9)
                                else
                                    ComposeColor(0xFFFFF3E0)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status indicator
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (data.status == "APPROVED")
                                            androidx.compose.material.icons.Icons.Default.CheckCircle
                                        else
                                            androidx.compose.material.icons.Icons.Default.Schedule,
                                        contentDescription = data.status,
                                        tint = if (data.status == "APPROVED")
                                            ComposeColor(0xFF4CAF50)
                                        else
                                            ComposeColor(0xFFFF9800),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(
                                        data.title,
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                    )
                                    Text(
                                        address,
                                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                        color = ComposeColor.Gray
                                    )
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        androidx.compose.material3.AssistChip(
                                            onClick = { },
                                            label = {
                                                Text(
                                                    data.crimeType.replace('_', ' '),
                                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = androidx.compose.material.icons.Icons.Default.Category,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        )
                                        if (data.approvers.isNotEmpty()) {
                                            androidx.compose.material3.AssistChip(
                                                onClick = { },
                                                label = {
                                                    Text(
                                                        "${data.approvers.size} approval${if (data.approvers.size != 1) "s" else ""}",
                                                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = androidx.compose.material.icons.Icons.Default.ThumbUp,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }

                                if (isAdmin) {
                                    IconButton(onClick = { onDeleteMarker(data) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete report",
                                            tint = ComposeColor(0xFFD32F2F)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = ComposeColor(0xFF2196F3)
                )
            ) {
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