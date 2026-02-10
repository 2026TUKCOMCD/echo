package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.ConversationEndResponse
import com.example.graduation_project.data.model.ConversationMessageResponse
import com.example.graduation_project.data.model.ConversationStartResponse
import com.example.graduation_project.data.model.HealthData
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ConversationApi {

    @POST("/api/conversations/start")
    suspend fun startConversation(
        @Body healthData: HealthData
    ): ConversationStartResponse

    @Multipart
    @POST("/api/conversations/message")
    suspend fun sendMessage(
        @Part audio: MultipartBody.Part
    ): ConversationMessageResponse

    @POST("/api/conversations/end")
    suspend fun endConversation(): ConversationEndResponse
}
