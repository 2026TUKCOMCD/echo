package com.example.graduation_project.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.graduation_project.data.model.RawLocationData
import com.example.graduation_project.data.model.RawVisitedPlace
import com.example.graduation_project.domain.health.StayPointDetector
import com.example.graduation_project.domain.model.LocationPoint
import com.example.graduation_project.domain.model.StayPoint
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 위치 데이터 수집 및 처리 매니저
 *
 * Room DB에 저장된 GPS 데이터를 기반으로:
 * - 현재 위치 조회
 * - StayPoint 알고리즘으로 방문 장소 계산
 * - 총 이동 거리 계산
 */
class LocationDataManager(
    private val context: Context,
    private val locationManager: LocationManager,
    private val locationStorageManager: LocationStorageManager,
    private val stayPointDetector: StayPointDetector
) {

    /**
     * 위치 데이터 수집
     *
     * @return 현재 위치 + 방문 장소 + 총 이동거리, 권한 없거나 위치 가져올 수 없으면 null
     */
    suspend fun collectLocationData(): RawLocationData? {
        Log.d(TAG, "========== LocationDataManager 위치 데이터 수집 시작 ==========")

        // 0. 위치 권한 체크
        val hasFineLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarseLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "위치 권한 상태: FINE=$hasFineLocation, COARSE=$hasCoarseLocation")

        if (!hasFineLocation && !hasCoarseLocation) {
            Log.w(TAG, "위치 권한 없음 - null 반환")
            return null
        }

        // 1. 현재 위치 — null이면 위치 기반 대화 불가이므로 전체 반환 null
        val currentLocation = locationManager.getCurrentLocation()
        if (currentLocation == null) {
            Log.w(TAG, "현재 위치를 가져올 수 없음 - null 반환")
            return null
        }
        Log.d(TAG, "현재 위치: lat=${currentLocation.latitude}, lon=${currentLocation.longitude}")

        // 2. Room DB에서 오늘 수집된 GPS 좌표 로드
        Log.d(TAG, "--- Room DB에서 오늘 GPS 좌표 조회 ---")
        val todayLocations = locationStorageManager.getTodayLocations()
        Log.d(TAG, "오늘 수집된 GPS 좌표: ${todayLocations.size}개")

        todayLocations.take(5).forEachIndexed { index, loc ->
            Log.d(TAG, "  [$index] lat=${loc.latitude}, lon=${loc.longitude}, time=${loc.timestamp}")
        }
        if (todayLocations.size > 5) {
            Log.d(TAG, "  ... (${todayLocations.size - 5}개 더)")
        }

        // 3. StayPoint 계산 — locations < 2이면 emptyList 반환
        val stayPoints = stayPointDetector.detect(todayLocations)
        Log.d(TAG, "감지된 StayPoint: ${stayPoints.size}개")
        stayPoints.forEachIndexed { index, sp ->
            Log.d(TAG, "  StayPoint[$index]: lat=${sp.latitude}, lon=${sp.longitude}, " +
                    "duration=${sp.durationMinutes}분, time=${sp.startTime}~${sp.endTime}")
        }

        // 4. 총 이동 거리 계산
        val totalDistance = calculateTotalDistance(todayLocations)
        Log.d(TAG, "총 이동 거리: ${String.format("%.2f", totalDistance)} km")

        val result = RawLocationData(
            currentLatitude = currentLocation.latitude,
            currentLongitude = currentLocation.longitude,
            visitedPlaces = stayPoints.map { it.toRawVisitedPlace() },
            totalDistanceKm = totalDistance
        )

        Log.d(TAG, "========== 최종 RawLocationData ==========")
        Log.d(TAG, "  currentLatitude: ${result.currentLatitude}")
        Log.d(TAG, "  currentLongitude: ${result.currentLongitude}")
        Log.d(TAG, "  visitedPlaces: ${result.visitedPlaces.size}개")
        result.visitedPlaces.forEachIndexed { index, place ->
            Log.d(TAG, "    [$index] lat=${place.latitude}, lon=${place.longitude}, " +
                    "time=${place.visitStartTime}~${place.visitEndTime}, stay=${place.stayDurationMinutes}분")
        }
        Log.d(TAG, "  totalDistanceKm: ${result.totalDistanceKm}")
        Log.d(TAG, "==========================================")

        return result
    }

    private fun calculateTotalDistance(locations: List<LocationPoint>): Double {
        if (locations.size < 2) return 0.0
        var total = 0.0
        for (i in 1 until locations.size) {
            total += haversineDistance(
                locations[i - 1].latitude, locations[i - 1].longitude,
                locations[i].latitude, locations[i].longitude
            )
        }
        return total / 1000.0  // meters → km
    }

    private fun haversineDistance(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_METERS * c
    }

    private fun StayPoint.toRawVisitedPlace(): RawVisitedPlace {
        val zone = ZoneId.systemDefault()
        return RawVisitedPlace(
            latitude = latitude,
            longitude = longitude,
            visitStartTime = LocalDateTime.ofInstant(startTime, zone).toLocalTime().toString(),
            visitEndTime = LocalDateTime.ofInstant(endTime, zone).toLocalTime().toString(),
            stayDurationMinutes = durationMinutes
        )
    }

    companion object {
        private const val TAG = "LocationDataManager"
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
