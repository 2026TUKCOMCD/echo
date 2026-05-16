package com.example.graduation_project.data.health

import com.example.graduation_project.domain.health.StayPointDetector
import com.example.graduation_project.domain.model.LocationPoint
import com.example.graduation_project.domain.model.StayPoint
import java.time.Duration
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Li et al. (2008) 기반 체류 지점 감지 구현체.
 *
 * 알고리즘:
 * 1. 첫 좌표를 anchor로 설정
 * 2. 순차 탐색: Haversine 거리 <= STAY_RADIUS면 같은 클러스터로 묶음
 * 3. 거리 초과 시 클러스터 체류 시간 확인 → MIN_STAY_DURATION 이상이면 StayPoint 저장
 * 4. 마지막 클러스터도 동일하게 처리
 *
 * StayPoint 좌표: 클러스터 내 모든 좌표의 centroid (평균값)
 *
 * @param stayRadiusMeters 체류 판단 반경 (기본값: 50m)
 * @param minStayDurationMinutes 최소 체류 시간 (기본값: 10분)
 */
class StayPointDetectorImpl(
    private val stayRadiusMeters: Double = STAY_RADIUS,
    private val minStayDurationMinutes: Long = MIN_STAY_DURATION
) : StayPointDetector {

    override fun detect(locations: List<LocationPoint>): List<StayPoint> {
        if (locations.size < 2) return emptyList()

        val result = mutableListOf<StayPoint>()
        var anchorIndex = 0
        var clusterPoints = mutableListOf(locations[0])

        for (i in 1 until locations.size) {
            val current = locations[i]
            val anchor = locations[anchorIndex]
            val distance = haversineDistance(
                anchor.latitude, anchor.longitude,
                current.latitude, current.longitude
            )

            if (distance <= stayRadiusMeters) {
                clusterPoints.add(current)
            } else {
                val durationMinutes = Duration.between(
                    clusterPoints.first().timestamp,
                    clusterPoints.last().timestamp
                ).toMinutes()

                if (durationMinutes >= minStayDurationMinutes) {
                    result.add(buildStayPoint(clusterPoints))
                }

                anchorIndex = i
                clusterPoints = mutableListOf(current)
            }
        }

        // 마지막 클러스터 처리
        val durationMinutes = Duration.between(
            clusterPoints.first().timestamp,
            clusterPoints.last().timestamp
        ).toMinutes()

        if (durationMinutes >= minStayDurationMinutes) {
            result.add(buildStayPoint(clusterPoints))
        }

        return result
    }

    private fun buildStayPoint(points: List<LocationPoint>): StayPoint {
        val centroidLat = points.map { it.latitude }.average()
        val centroidLng = points.map { it.longitude }.average()
        return StayPoint(
            latitude = centroidLat,
            longitude = centroidLng,
            startTime = points.first().timestamp,
            endTime = points.last().timestamp,
            durationMinutes = Duration.between(
                points.first().timestamp,
                points.last().timestamp
            ).toMinutes().toInt()
        )
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

    companion object {
        const val STAY_RADIUS = 50.0       // 미터
        const val MIN_STAY_DURATION = 10L  // 분
        private const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
