package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.ConversationEndResponse
import com.example.graduation_project.data.model.ConversationMessageResponse
import com.example.graduation_project.data.model.ConversationStartResponse
import com.example.graduation_project.data.model.ConversationStartRequest
import com.example.graduation_project.data.model.TtsRetryResponse
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming

interface ConversationApi {

    companion object {
        const val MESSAGE_STREAM_PATH = "/api/conversations/message-stream"
        const val START_STREAM_PATH = "/api/conversations/start-stream"
    }

    @POST("/api/conversations/start")
    suspend fun startConversation(
        @Body request: ConversationStartRequest
    ): ConversationStartResponse

    /**
     * 대화 시작의 스트리밍 응답 버전. 프레임 규격은 [sendMessageStream]과 같고,
     * 첫 인사에는 사용자 발화가 없어 META의 userMessage는 항상 null이다.
     */
    @Streaming
    @POST(START_STREAM_PATH)
    suspend fun startConversationStream(
        @Body request: ConversationStartRequest
    ): Response<ResponseBody>

    @Multipart
    @POST("/api/conversations/message")
    suspend fun sendMessage(
        @Part audio: MultipartBody.Part
    ): ConversationMessageResponse

    /**
     * 스트리밍 응답 버전 (프레임 스트림, [ConversationStreamProtocol] 참고).
     * - @Streaming: 본문을 메모리에 모으지 않고 도착하는 대로 읽기 위함
     * - Response<ResponseBody>: 404/405(서버에 엔드포인트 없음)를 예외 없이 구분해 폴백하기 위함
     */
    @Streaming
    @Multipart
    @POST(MESSAGE_STREAM_PATH)
    suspend fun sendMessageStream(
        @Part audio: MultipartBody.Part
    ): Response<ResponseBody>

    @POST("/api/conversations/end")
    suspend fun endConversation(): ConversationEndResponse

    @POST("/api/conversations/tts-retry")
    suspend fun retryTts(): TtsRetryResponse
}
