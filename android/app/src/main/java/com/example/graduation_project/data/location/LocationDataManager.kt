package com.example.graduation_project.data.location

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
        // 1. 현재 위치 — null이면 위치 기반 대화 불가이므로 전체 반환 null
        val currentLocation = locationManager.getCurrentLocation() ?: return null

        // 2. 오늘 운동 세션별 GPS 좌표 (route 없거나 좌표 < 2인 세션은 HealthConnectManager에서 제외됨)
        val exerciseSessions = healthConnectManager.readExerciseSessionLocations()
        val allLocations = exerciseSessions.flatten()

        // 3. StayPoint 계산 — locations < 2이면 StayPointDetectorImpl이 emptyList 반환
        val stayPoints = stayPointDetector.detect(allLocations)

        // 4. 총 이동 거리 — 운동 기록 없으면 0.0
        val totalDistance = calculateTotalDistance(allLocations)

        return RawLocationData(
            currentLatitude = currentLocation.latitude,
            currentLongitude = currentLocation.longitude,
            visitedPlaces = stayPoints.map { it.toRawVisitedPlace() },
            totalDistanceKm = totalDistance
        )
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
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
