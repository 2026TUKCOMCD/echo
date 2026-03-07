package com.tukcomcd.echo.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Diary(
    val id: Long,
    val title: String,
    val content: String,
    val createdAt: String,
    val weather: String? = null,
    val mood: String? = null
)
