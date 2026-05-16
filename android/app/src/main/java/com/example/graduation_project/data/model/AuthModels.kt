package com.example.graduation_project.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SignupRequest(
    val loginId: String,
    val password: String,
    val name: String
)

@Serializable
data class LoginRequest(
    val loginId: String,
    val password: String
)

@Serializable
data class RefreshRequest(
    val refreshToken: String
)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String
)
