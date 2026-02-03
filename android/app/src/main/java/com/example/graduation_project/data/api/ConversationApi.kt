package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.ConversationResponse
import com.example.graduation_project.data.model.HealthData
import com.example.graduation_project.data.model.MessageRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ConversationApi {

    @POST("/api/conversations/start")
    suspend fun startConversation(
        @Body healthData: HealthData
    ): ConversationResponse

    @Multipart
    @POST("/api/conversations/message")
    suspend fun sendMessage(
        @Part audio: MultipartBody.Part
    ): ConversationResponse

    @POST("/api/conversations/end")
    suspend fun endConversation(): ConversationResponse
}
