package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.ConsentResponse
import com.example.graduation_project.data.model.ConsentUpdateRequest
import com.example.graduation_project.data.model.RoutinePlaceConfirmRequest
import com.example.graduation_project.data.model.RoutinePlaceResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path

interface RoutinePlaceApi {

    @GET("/api/users/me/routine-places/consent")
    suspend fun getConsent(): ConsentResponse

    @PUT("/api/users/me/routine-places/consent")
    suspend fun updateConsent(@Body request: ConsentUpdateRequest): ConsentResponse

    @GET("/api/users/me/routine-places/candidates")
    suspend fun getCandidates(): List<RoutinePlaceResponse>

    @GET("/api/users/me/routine-places")
    suspend fun getConfirmedPlaces(): List<RoutinePlaceResponse>

    @PUT("/api/users/me/routine-places/{id}/confirm")
    suspend fun confirm(@Path("id") id: Long, @Body request: RoutinePlaceConfirmRequest): RoutinePlaceResponse

    @PUT("/api/users/me/routine-places/{id}")
    suspend fun updateCategory(@Path("id") id: Long, @Body request: RoutinePlaceConfirmRequest): RoutinePlaceResponse

    @DELETE("/api/users/me/routine-places/{id}")
    suspend fun delete(@Path("id") id: Long)
}
