package com.example.graduation_project.domain.location

import java.time.Instant

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant
)
