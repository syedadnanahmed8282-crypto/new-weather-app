package com.example.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority

class MainActivity : ComponentActivity() {
    private val vm: WeatherViewModel by viewModels()
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var updating = false
    private var pendingForce = false

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { vm.onLocation(it.latitude, it.longitude) }
        }
    }

    // Permission pop-up
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants.values.any { it }) {
                if (grants[Manifest.permission.ACCESS_FINE_LOCATION] != true) {
                    toast("Using approximate location. Allow \"Precise\" location in the app permissions for the exact area name.")
                }
                checkLocationSwitch(pendingForce)
            } else {
                toast("Location permission is off. To use live location, allow it in Settings > Apps > Weather > Permissions.")
            }
        }

    // "Turn on location?" pop-up from Google Play services
    private val resolutionLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                startUpdates()
                fetchOnce(force = pendingForce, loud = true)
            } else {
                toast("Location is still off. Turn it on to use live location.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WeatherApp(
                vm,
                onRequestLocation = { requestLocation(force = true) },
                onRefreshLocation = ::refreshLocationQuietly,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        val prefs = getSharedPreferences("weather", MODE_PRIVATE)
        if (hasLocationPermission()) {
            if (isLocationSwitchOn()) {
                startUpdates()
                fetchOnce(force = false, loud = false)
            }
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

    // ---------------------------------------------------------------- permission and settings

    private fun hasLocationPermission() =
        listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION).any {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

    private fun isLocationSwitchOn(): Boolean {
        val lm = getSystemService(LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(lm)
    }

    /** The "Use current location" button. Asks for permission, then offers to switch Location on, then finds the position. */
    private fun requestLocation(force: Boolean) {
        pendingForce = force
        if (!hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        } else {
            checkLocationSwitch(force)
        }
    }

    private fun checkLocationSwitch(force: Boolean) {
        pendingForce = force
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10_000L).build()
        val settings = LocationSettingsRequest.Builder().addLocationRequest(request).setAlwaysShow(true).build()
        LocationServices.getSettingsClient(this).checkLocationSettings(settings)
            .addOnSuccessListener {
                startUpdates()
                fetchOnce(force, loud = true)
            }
            .addOnFailureListener { e ->
                if (e is ResolvableApiException) {
                    try {
                        resolutionLauncher.launch(IntentSenderRequest.Builder(e.resolution).build())
                    } catch (_: Exception) {
                        toast("Location is off. Please turn it on in your phone settings.")
                    }
                } else {
                    toast("Location is off. Please turn it on in your phone settings.")
                }
            }
    }

    // ---------------------------------------------------------------- finding the position

    /** Pull-to-refresh: update the position too, without any pop-ups or messages. */
    private fun refreshLocationQuietly() {
        if (hasLocationPermission() && isLocationSwitchOn()) fetchOnce(force = false, loud = false)
    }

    @SuppressLint("MissingPermission")
    private fun fetchOnce(force: Boolean, loud: Boolean) {
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setDurationMillis(15_000L)
            .setMaxUpdateAgeMillis(30_000L)
            .build()
        fused.getCurrentLocation(request, null)
            .addOnSuccessListener { loc ->
                if (loc != null) vm.onLocation(loc.latitude, loc.longitude, force)
                else if (loud) toast("Couldn't find your location yet. Try near a window or in the open and tap again.")
            }
            .addOnFailureListener { e ->
                if (loud) toast("Couldn't get your location: ${e.message ?: "unknown error"}")
            }
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

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}
