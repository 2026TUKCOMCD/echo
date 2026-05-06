package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.LoginRequest
import com.example.graduation_project.data.model.RefreshRequest
import com.example.graduation_project.data.model.SignupRequest
import com.example.graduation_project.data.model.TokenResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApi {

    @POST("/api/auth/signup")
    suspend fun signup(@Body request: SignupRequest): TokenResponse

    @POST("/api/auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    @POST("/api/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): TokenResponse

    @POST("/api/auth/logout")
    suspend fun logout(
        @Header("Authorization") authorization: String,
        @Body request: RefreshRequest
    )
}
