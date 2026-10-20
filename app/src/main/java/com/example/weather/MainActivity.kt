package com.example.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var updating = false
    private var forceAfterPermission = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { vm.onLocation(it.latitude, it.longitude) }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) {
                startUpdates()
                fetchOnce(force = forceAfterPermission)
            }
            forceAfterPermission = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { WeatherApp(vm, onRequestLocation = { requestLocation(force = true) }) }
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("weather", MODE_PRIVATE)
        if (hasLocationPermission()) {
            startUpdates()
            fetchOnce(force = false)
        } else if (!prefs.getBoolean("asked_location", false)) {
            // First launch: offer live location once
            prefs.edit().putBoolean("asked_location", true).apply()
            requestLocation(force = true)
        }
    }

    override fun onStop() {
        stopUpdates()          // live location only runs while the app is on screen
        super.onStop()
    }

    private fun hasLocationPermission() =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun requestLocation(force: Boolean) {
        if (hasLocationPermission()) {
            startUpdates()
            fetchOnce(force)
        } else {
            forceAfterPermission = force
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchOnce(force: Boolean) {
        fused.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { loc -> if (loc != null) vm.onLocation(loc.latitude, loc.longitude, force) }
    }

    // A new position is reported every 10 minutes at most, and only after moving 2 km or more.
    @SuppressLint("MissingPermission")
    private fun startUpdates() {
        if (updating || !hasLocationPermission()) return
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10 * 60 * 1000L)
            .setMinUpdateDistanceMeters(2000f)
            .build()
        fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
        updating = true
    }

    private fun stopUpdates() {
        if (!updating) return
        fused.removeLocationUpdates(callback)
        updating = false
    }
}
