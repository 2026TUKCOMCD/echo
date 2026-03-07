package com.tukcomcd.echo.data.repository

import com.tukcomcd.echo.data.api.ApiClient
import com.tukcomcd.echo.data.api.ApiResult
import com.tukcomcd.echo.data.api.safeApiCall
import com.tukcomcd.echo.data.api.ConversationApi
import com.tukcomcd.echo.data.model.ConversationEndResponse
import com.tukcomcd.echo.data.model.ConversationMessageResponse
import com.tukcomcd.echo.data.model.ConversationStartResponse
import com.tukcomcd.echo.data.model.HealthData
import com.tukcomcd.echo.data.model.TtsRetryResponse
import okhttp3.MultipartBody

class ConversationRepository(
    private val conversationApi: ConversationApi = ApiClient.conversationApi
) {

    suspend fun startConversation(healthData: HealthData): ApiResult<ConversationStartResponse> {
        return safeApiCall {
            conversationApi.startConversation(healthData)
        }
    }

    suspend fun sendMessage(audio: MultipartBody.Part): ApiResult<ConversationMessageResponse> {
        return safeApiCall {
            conversationApi.sendMessage(audio)
        }
    }

    suspend fun endConversation(): ApiResult<ConversationEndResponse> {
        return safeApiCall {
            conversationApi.endConversation()
        }
    }

    suspend fun retryTts(): ApiResult<TtsRetryResponse> {
        return safeApiCall {
            conversationApi.retryTts()
        }
    }
}
