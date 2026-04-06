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
        private const val LOCATION_TIMEOUT_MS = 5_000L
    }

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        Log.d(TAG, "getCurrentLocation() 호출 - 타임아웃: ${LOCATION_TIMEOUT_MS}ms")

        val location = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val cancellationToken = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    cancellationToken.token
                ).addOnSuccessListener { loc ->
                    if (loc != null) {
                        Log.d(TAG, "✅ getCurrentLocation 성공: lat=${loc.latitude}, lon=${loc.longitude}")
                    } else {
                        Log.w(TAG, "⚠️ getCurrentLocation 성공했지만 위치가 null")
                    }
                    continuation.resume(loc)
                }.addOnFailureListener { e ->
                    Log.e(TAG, "❌ getCurrentLocation 실패: ${e.message}", e)
                    continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    Log.d(TAG, "getCurrentLocation 취소됨")
                    cancellationToken.cancel()
                }
            }
        }

        if (location == null) {
            Log.w(TAG, "⚠️ getCurrentLocation 타임아웃 또는 null - lastLocation 시도")
            return getLastKnownLocation()
        }

        return location
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastKnownLocation(): Location? =
        suspendCancellableCoroutine { continuation ->
            fusedLocationClient.lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        Log.d(TAG, "✅ lastLocation 성공: lat=${loc.latitude}, lon=${loc.longitude}")
                    } else {
                        Log.w(TAG, "⚠️ lastLocation도 null - 위치 기록 없음")
                    }
                    continuation.resume(loc)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ lastLocation 실패: ${e.message}", e)
                    continuation.resume(null)
                }
        }
}
