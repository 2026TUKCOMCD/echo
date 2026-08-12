package com.example.graduation_project.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
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
        private const val TAG = "LocationManager"
        // 집 등록은 사용자가 한 번 누르는 액션이라 정확도를 위해 여유 있게 대기
        private const val LOCATION_TIMEOUT_MS = 15_000L
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val cancellationToken = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationToken.token
                ).addOnSuccessListener { loc ->
                    Log.d(TAG, "getCurrentLocation success: ${loc?.let { "(${it.latitude}, ${it.longitude})" } ?: "null"}")
                    continuation.resume(loc)
                }.addOnFailureListener { e ->
                    Log.w(TAG, "getCurrentLocation failed", e)
                    continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    cancellationToken.cancel()
                }
            }
        }

        if (location == null) {
            Log.w(TAG, "getCurrentLocation null (타임아웃 ${LOCATION_TIMEOUT_MS}ms 또는 미가용) → lastLocation 폴백")
        }
        return location ?: getLastKnownLocation()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { loc ->
                    Log.d(TAG, "lastLocation: ${loc?.let { "(${it.latitude}, ${it.longitude})" } ?: "null"}")
                    continuation.resume(loc)
                }
                .addOnFailureListener { e ->
                    Log.w(TAG, "lastLocation failed", e)
                    continuation.resume(null)
                }
        }
}
