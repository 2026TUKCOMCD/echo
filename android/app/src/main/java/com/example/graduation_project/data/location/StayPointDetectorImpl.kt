package com.example.graduation_project.data.location

import com.example.graduation_project.domain.location.LocationPoint
import com.example.graduation_project.domain.location.StayPoint
import com.example.graduation_project.domain.location.StayPointDetector
import java.time.Duration
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class StayPointDetectorImpl : StayPointDetector {

    companion object {
        const val STAY_RADIUS_METERS = 50.0
        const val MIN_STAY_DURATION_MINUTES = 10L
        const val MAX_TIME_GAP_MINUTES = 30L
    }

    override fun detect(locations: List<LocationPoint>): List<StayPoint> {
        val valid = locations.filter { isValidCoordinate(it) }
        if (valid.size < 2) return emptyList()

        val stayPoints = mutableListOf<StayPoint>()
        var anchorIndex = 0

        for (i in 1 until valid.size) {
            val anchor = valid[anchorIndex]
            val current = valid[i]

            val gap = Duration.between(valid[i - 1].timestamp, current.timestamp)
            if (gap > Duration.ofMinutes(MAX_TIME_GAP_MINUTES)) {
                val duration = Duration.between(anchor.timestamp, valid[i - 1].timestamp).toMinutes()
                if (duration >= MIN_STAY_DURATION_MINUTES) {
                    stayPoints.add(createStayPoint(valid, anchorIndex, i - 1))
                }
                anchorIndex = i
                continue
            }

            val distance = haversineDistance(anchor, current)

            if (distance > STAY_RADIUS_METERS) {
                val duration = Duration.between(anchor.timestamp, valid[i - 1].timestamp).toMinutes()
                if (duration >= MIN_STAY_DURATION_MINUTES) {
                    stayPoints.add(createStayPoint(valid, anchorIndex, i - 1))
                }
                anchorIndex = i
            }
        }

        handleLastSegment(valid, anchorIndex, stayPoints)
        return stayPoints
    }

    private fun createStayPoint(locations: List<LocationPoint>, start: Int, end: Int): StayPoint {
        val cluster = locations.subList(start, end + 1)
        val centroidLat = cluster.sumOf { it.latitude } / cluster.size
        val centroidLng = cluster.sumOf { it.longitude } / cluster.size
        return StayPoint(
            latitude = centroidLat,
            longitude = centroidLng,
            startTime = cluster.first().timestamp,
            endTime = cluster.last().timestamp,
            durationMinutes = Duration.between(cluster.first().timestamp, cluster.last().timestamp)
                .toMinutes()
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        )
    }

    private fun handleLastSegment(
        locations: List<LocationPoint>,
        anchorIndex: Int,
        stayPoints: MutableList<StayPoint>
    ) {
        val duration = Duration.between(
            locations[anchorIndex].timestamp,
            locations.last().timestamp
        ).toMinutes()
        if (duration >= MIN_STAY_DURATION_MINUTES) {
            stayPoints.add(createStayPoint(locations, anchorIndex, locations.size - 1))
        }
    }

    internal fun haversineDistance(p1: LocationPoint, p2: LocationPoint): Double {
        val R = 6_371_000.0
        val lat1 = Math.toRadians(p1.latitude)
        val lat2 = Math.toRadians(p2.latitude)
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }

    private fun isValidCoordinate(point: LocationPoint): Boolean {
        if (point.latitude.isNaN() || point.longitude.isNaN()) return false
        if (point.latitude !in -90.0..90.0) return false
        if (point.longitude !in -180.0..180.0) return false
        return true
    }
}
