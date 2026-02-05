package com.example.graduation_project.data.repository

import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.model.ConversationResponse
import com.example.graduation_project.data.api.ConversationApi
import com.example.graduation_project.data.model.HealthData
import okhttp3.MultipartBody

class ConversationRepository(
    private val conversationApi: ConversationApi = ApiClient.conversationApi
) {

    suspend fun startConversation(healthData: HealthData): ApiResult<ConversationResponse> {
        return safeApiCall {
            conversationApi.startConversation(healthData)
        }
    }

    suspend fun sendMessage(audio: MultipartBody.Part): ApiResult<ConversationResponse> {
        return safeApiCall {
            conversationApi.sendMessage(audio)
        }
    }

    suspend fun endConversation(): ApiResult<ConversationResponse> {
        return safeApiCall {
            conversationApi.endConversation()
        }
    }
}
