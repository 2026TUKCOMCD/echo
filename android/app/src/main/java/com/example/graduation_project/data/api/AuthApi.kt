package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.SignupRequest
import com.example.graduation_project.data.model.TokenResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {

    @POST("/api/auth/signup")
    suspend fun signup(@Body request: SignupRequest): TokenResponse
}
