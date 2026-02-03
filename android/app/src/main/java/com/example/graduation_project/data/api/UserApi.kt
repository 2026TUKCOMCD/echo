package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.User
import com.example.graduation_project.data.model.UserPreferences
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface UserApi {

    @GET("/api/users/me")
    suspend fun getUser(): User

    @PUT("/api/users/me")
    suspend fun updateUser(@Body user: User): User

    @GET("/api/users/me/preferences")
    suspend fun getPreferences(): UserPreferences

    @PUT("/api/users/me/preferences")
    suspend fun updatePreferences(@Body preferences: UserPreferences): UserPreferences
}
