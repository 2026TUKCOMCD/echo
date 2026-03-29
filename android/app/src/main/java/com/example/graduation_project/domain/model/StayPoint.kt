package com.example.graduation_project.domain.model

import java.time.Instant

data class StayPoint(
    val latitude: Double,
    val longitude: Double,
    val startTime: Instant,
    val endTime: Instant,
    val durationMinutes: Int
)
