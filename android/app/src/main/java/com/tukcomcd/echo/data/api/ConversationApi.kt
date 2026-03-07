package com.tukcomcd.echo.data.api

import com.tukcomcd.echo.data.model.ConversationEndResponse
import com.tukcomcd.echo.data.model.ConversationMessageResponse
import com.tukcomcd.echo.data.model.ConversationStartResponse
import com.tukcomcd.echo.data.model.HealthData
import com.tukcomcd.echo.data.model.TtsRetryResponse
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

    @POST("/api/conversations/tts-retry")
    suspend fun retryTts(): TtsRetryResponse
}
