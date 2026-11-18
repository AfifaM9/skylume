package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.example.data.model.LocationItem
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

class CoarseLocationClient(private val context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Result<LocationItem> = withContext(Dispatchers.IO) {
        runCatching {
            val location = fetchCoarseCoordinates()
                ?: throw IllegalStateException("Unable to determine current location. Please check device location settings.")

            val address = reverseGeocode(location.latitude, location.longitude)
            val cityName = address?.locality
                ?: address?.subAdminArea
                ?: address?.adminArea
                ?: "Current Location"
            val countryName = address?.countryName.orEmpty()
            val admin1 = address?.adminArea

            LocationItem(
                id = "gps_${location.latitude}_${location.longitude}",
                name = cityName,
                country = countryName,
                admin1 = admin1,
                latitude = location.latitude,
                longitude = location.longitude,
                timezone = "auto"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchCoarseCoordinates(): android.location.Location? =
        suspendCancellableCoroutine { cont ->
            val cts = CancellationTokenSource()
            cont.invokeOnCancellation { cts.cancel() }

            fusedClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token)
                .addOnSuccessListener { current ->
                    if (current != null) {
                        cont.resume(current)
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { last -> cont.resume(last) }
                            .addOnFailureListener { cont.resume(null) }
                    }
                }
                .addOnFailureListener {
                    fusedClient.lastLocation
                        .addOnSuccessListener { last -> cont.resume(last) }
                        .addOnFailureListener { cont.resume(null) }
                }
        }

    @Suppress("DEPRECATION")
    private suspend fun reverseGeocode(lat: Double, lon: Double): Address? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { cont ->
                try {
                    geocoder.getFromLocation(lat, lon, 1) { addresses ->
                        cont.resume(addresses.firstOrNull())
                    }
                } catch (_: Exception) {
                    cont.resume(null)
                }
            }
        } else {
            runCatching {
                geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()
            }.getOrNull()
        }
    }
}
