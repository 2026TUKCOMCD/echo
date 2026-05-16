package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val description: String,
    val temperature: Int
)
