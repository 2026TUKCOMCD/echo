package com.example.graduation_project.domain.model

import java.time.Instant

data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Instant
)
