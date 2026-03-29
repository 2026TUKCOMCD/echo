package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RawVisitedPlace(
    val latitude: Double,
    val longitude: Double,
    val visitStartTime: String,       // ISO-8601 (session.startTime.toString())
    val visitEndTime: String,         // ISO-8601 (session.endTime.toString())
    val stayDurationMinutes: Int
)

@Serializable
data class RawLocationData(
    val currentLatitude: Double?,                               // 현재 위치 위도 (A2 연동 후 채워짐)
    val currentLongitude: Double?,                              // 현재 위치 경도 (A2 연동 후 채워짐)
    val visitedPlaces: List<RawVisitedPlace> = emptyList(),     // 오늘 운동 경로 방문 장소
    val totalDistanceKm: Double? = null                         // 오늘 총 운동 거리 (HealthData.exerciseDistanceKm)
)
