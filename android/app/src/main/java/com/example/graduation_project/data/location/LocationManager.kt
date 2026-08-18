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
import kotlinx.coroutines.delay
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

        // 집 등록용 다중 샘플링 설정 - 실내 GPS 오차(단발 측정 시 100m+ 흔함)를 평균으로 줄인다
        private const val HOME_REGISTRATION_SAMPLE_COUNT = 3
        private const val HOME_REGISTRATION_SAMPLE_DELAY_MS = 1_000L
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

    /**
     * 집 등록 전용: GPS를 여러 번 찍어 평균 낸 위치를 반환한다.
     *
     * 단발 측정([getCurrentLocation])은 실내에서 100m 이상 오차가 흔해, 집 좌표를 기준으로
     * 방문지를 집/외출로 분류할 때(HOME_MATCH_RADIUS_METERS) 오분류를 유발할 수 있다.
     * 집 등록은 사용자가 버튼을 한 번 누르는 저빈도 액션이므로, 몇 초 더 기다리더라도
     * 여러 샘플을 평균 내 오차를 줄이는 쪽이 낫다.
     */
    suspend fun getAveragedCurrentLocation(
        sampleCount: Int = HOME_REGISTRATION_SAMPLE_COUNT,
        sampleDelayMs: Long = HOME_REGISTRATION_SAMPLE_DELAY_MS
    ): Location? {
        val samples = mutableListOf<Location>()
        repeat(sampleCount) { index ->
            getCurrentLocation()?.let { samples.add(it) }
            if (index < sampleCount - 1) {
                delay(sampleDelayMs)
            }
        }

        Log.d(TAG, "getAveragedCurrentLocation - 유효 샘플 ${samples.size}/${sampleCount}개")
        return averageLocations(samples)
    }

    private fun averageLocations(samples: List<Location>): Location? {
        if (samples.isEmpty()) return null
        if (samples.size == 1) return samples[0]

        val avgLatitude = samples.sumOf { it.latitude } / samples.size
        val avgLongitude = samples.sumOf { it.longitude } / samples.size
        val avgAccuracy = samples.map { it.accuracy }.average().toFloat()

        return Location("averaged").apply {
            latitude = avgLatitude
            longitude = avgLongitude
            accuracy = avgAccuracy
            time = System.currentTimeMillis()
        }
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
