package com.example.graduation_project.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import androidx.annotation.VisibleForTesting
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class LocationManager @VisibleForTesting internal constructor(
    private val fusedLocationClient: FusedLocationProviderClient
) {
    constructor(context: Context) : this(
        LocationServices.getFusedLocationProviderClient(context)
    )

    companion object {
        private const val LOCATION_TIMEOUT_MS = 5_000L
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val cancellationToken = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationToken.token
                ).addOnSuccessListener { loc ->
                    continuation.resume(loc)
                }.addOnFailureListener {
                    continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    cancellationToken.cancel()
                }
            }
        }

        return location ?: getLastKnownLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { loc -> continuation.resume(loc) }
                .addOnFailureListener { continuation.resume(null) }
        }
}
