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
