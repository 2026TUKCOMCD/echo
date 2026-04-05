package com.example.graduation_project.data.location

import android.util.Log
import com.example.graduation_project.data.health.HealthConnectManager
import com.example.graduation_project.data.model.RawLocationData
import com.example.graduation_project.data.model.RawVisitedPlace
import com.example.graduation_project.domain.health.StayPointDetector
import com.example.graduation_project.domain.model.LocationPoint
import com.example.graduation_project.domain.model.StayPoint
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class LocationDataManager(
    private val locationManager: LocationManager,
    private val healthConnectManager: HealthConnectManager,
    private val stayPointDetector: StayPointDetector
) {

    suspend fun collectLocationData(): RawLocationData? {
        Log.d(TAG, "========== LocationDataManager 위치 데이터 수집 시작 ==========")

        // 1. 현재 위치 — null이면 위치 기반 대화 불가이므로 전체 반환 null
        val currentLocation = locationManager.getCurrentLocation()
        if (currentLocation == null) {
            Log.w(TAG, "⚠️ 현재 위치를 가져올 수 없음 - null 반환")
            return null
        }
        Log.d(TAG, "✅ 현재 위치: lat=${currentLocation.latitude}, lon=${currentLocation.longitude}")

        // 2. 오늘 운동 세션별 GPS 좌표 (route 없거나 좌표 < 2인 세션은 HealthConnectManager에서 제외됨)
        Log.d(TAG, "--- HealthConnect에서 운동 세션 GPS 좌표 조회 ---")
        val exerciseSessions = healthConnectManager.readExerciseSessionLocations()
        val allLocations = exerciseSessions.flatten()
        Log.d(TAG, "수집된 운동 세션: ${exerciseSessions.size}개, 총 GPS 좌표: ${allLocations.size}개")

        exerciseSessions.forEachIndexed { sessionIndex, locations ->
            Log.d(TAG, "  세션[$sessionIndex]: ${locations.size}개 좌표")
            locations.take(3).forEachIndexed { locIndex, loc ->
                Log.d(TAG, "    [$locIndex] lat=${loc.latitude}, lon=${loc.longitude}, time=${loc.timestamp}")
            }
            if (locations.size > 3) {
                Log.d(TAG, "    ... (${locations.size - 3}개 더)")
            }
        }

        // 3. StayPoint 계산 — locations < 2이면 StayPointDetectorImpl이 emptyList 반환
        val stayPoints = stayPointDetector.detect(allLocations)
        Log.d(TAG, "감지된 StayPoint: ${stayPoints.size}개")
        stayPoints.forEachIndexed { index, sp ->
            Log.d(TAG, "  StayPoint[$index]: lat=${sp.latitude}, lon=${sp.longitude}, " +
                    "duration=${sp.durationMinutes}분, time=${sp.startTime}~${sp.endTime}")
        }

        // 4. 총 이동 거리 — 운동 기록 없으면 0.0
        val totalDistance = calculateTotalDistance(allLocations)
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
            visitStartTime = LocalTime.ofInstant(startTime, zone).toString(),
            visitEndTime = LocalTime.ofInstant(endTime, zone).toString(),
            stayDurationMinutes = durationMinutes
        )
    }

    companion object {
        private const val TAG = "LocationDataManager"
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
