package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

@Serializable
data class RawVisitedPlace(
    val latitude: Double,
    val longitude: Double,
    val visitStartTime: String,       // "HH:mm:ss" 형식 (서버 LocalTime 직렬화)
    val visitEndTime: String,         // "HH:mm:ss" 형식
    val stayDurationMinutes: Int
)

@Serializable
data class RawLocationData(
    val currentLatitude: Double?,                               // 현재 위치 위도 (yuripiece A2 연동 후 채워짐)
    val currentLongitude: Double?,                              // 현재 위치 경도 (yuripiece A2 연동 후 채워짐)
    val visitedPlaces: List<RawVisitedPlace> = emptyList(),     // 오늘 운동 경로 방문 장소
    val totalDistanceKm: Double? = null                         // 오늘 총 운동 거리 (HealthData.exerciseDistanceKm)
)
