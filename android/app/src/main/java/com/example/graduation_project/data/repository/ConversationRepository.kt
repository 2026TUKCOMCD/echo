package com.example.graduation_project.data.repository

import android.util.Log
import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.AudioFrameInputStream
import com.example.graduation_project.data.api.ConversationStreamProtocol
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.api.ConversationApi
import com.example.graduation_project.data.model.ConversationEndResponse
import com.example.graduation_project.data.model.ConversationMessageResponse
import com.example.graduation_project.data.model.ConversationStartResponse
import com.example.graduation_project.data.model.ConversationStartRequest
import com.example.graduation_project.data.model.HealthData
import com.example.graduation_project.data.model.MessageReply
import com.example.graduation_project.data.model.RawLocationData
import com.example.graduation_project.data.model.TtsRetryResponse
import com.example.graduation_project.util.TurnLatencyTracker
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ConversationRepository(
    private val conversationApi: ConversationApi = ApiClient.conversationApi,
    private val streamSupport: StreamSupport = StreamSupport.processWide,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    /**
     * 서버의 스트리밍 엔드포인트 지원 여부 기억.
     * 한 번 404/405를 받으면 앱 프로세스가 살아 있는 동안 그 엔드포인트의 스트리밍을 시도하지 않는다
     * (매번 없는 주소로 요청해 시간을 낭비하지 않도록). 앱을 재시작하면 다시 시도한다.
     *
     * 엔드포인트마다 따로 기억한다 - 한쪽만 배포된 서버에서 `/start-stream` 404가
     * `/message-stream`까지 꺼버리면 멀쩡한 쪽의 지연 개선까지 잃기 때문이다.
     */
    class StreamSupport {
        @Volatile
        var messageUnsupported: Boolean = false

        @Volatile
        var startUnsupported: Boolean = false

        companion object {
            val processWide = StreamSupport()
        }
    }

    suspend fun startConversation(
        healthData: HealthData,
        locationData: RawLocationData?
    ): ApiResult<ConversationStartResponse> {
        return safeApiCall {
            conversationApi.startConversation(ConversationStartRequest(healthData, locationData))
        }
    }

    /**
     * 대화를 시작하고 AI 첫 인사를 받는다. 스트리밍(`/start-stream`)을 우선 사용한다.
     *
     * 폴백 규칙은 [sendMessageStreaming]과 같다: **404/405일 때만** 기존 `/start`로 다시 요청한다.
     * 서버가 경로 자체를 거절한 경우라 컨텍스트가 초기화되지 않았으므로 다시 시작해도 안전하다.
     * 그 외 실패(5xx, 타임아웃)에서는 서버가 이미 대화를 시작했을 수 있어 재요청하지 않는다.
     *
     * 첫 인사에는 사용자 발화가 없으므로 [MessageReply.userMessage]는 항상 null이다.
     * 성공 시 [MessageReply.Streaming]의 오디오 스트림은 호출자가 소비하고 닫아야 한다.
     */
    suspend fun startConversationStreaming(
        healthData: HealthData,
        locationData: RawLocationData?
    ): ApiResult<MessageReply> {
        if (streamSupport.startUnsupported) return startConversationBuffered(healthData, locationData)

        val request = ConversationStartRequest(healthData, locationData)
        return when (val result = safeApiCall { openStream { conversationApi.startConversationStream(request) } }) {
            is ApiResult.Success -> {
                val streaming = result.data
                if (streaming != null) {
                    ApiResult.Success(streaming)
                } else {
                    streamSupport.startUnsupported = true
                    Log.w(TAG, "서버에 대화 시작 스트리밍 엔드포인트가 없어 /start로 폴백 (이후 이 프로세스에서는 스트리밍 미사용)")
                    startConversationBuffered(healthData, locationData)
                }
            }
            is ApiResult.Error -> result
        }
    }

    private suspend fun startConversationBuffered(
        healthData: HealthData,
        locationData: RawLocationData?
    ): ApiResult<MessageReply> {
        return when (val result = startConversation(healthData, locationData)) {
            is ApiResult.Success -> ApiResult.Success(
                MessageReply.Buffered(
                    userMessage = null,
                    aiResponse = result.data.message,
                    audioData = result.data.audioData
                )
            )
            is ApiResult.Error -> result
        }
    }

    suspend fun sendMessage(audio: MultipartBody.Part): ApiResult<ConversationMessageResponse> {
        return safeApiCall {
            conversationApi.sendMessage(audio)
        }
    }

    /**
     * 음성 메시지를 보내고 AI 응답을 받는다. 스트리밍(`/message-stream`)을 우선 사용한다.
     *
     * 폴백 규칙: 스트리밍 엔드포인트가 **없을 때(404/405)만** 기존 `/message`로 다시 보낸다.
     * 이 경우 서버는 본문을 처리하기 전에 거절하므로 같은 음성을 다시 보내도 대화 기록이 중복되지 않는다.
     * 그 외 실패(5xx, 타임아웃, 연결 끊김)에서는 서버가 이미 그 턴을 처리했을 수 있어 재전송하지 않는다.
     *
     * 성공 시 [MessageReply.Streaming]의 오디오 스트림은 호출자가 소비하고 닫아야 한다.
     */
    suspend fun sendMessageStreaming(audio: MultipartBody.Part): ApiResult<MessageReply> {
        if (streamSupport.messageUnsupported) return sendMessageBuffered(audio)

        return when (val result = safeApiCall { openStream { conversationApi.sendMessageStream(audio) } }) {
            is ApiResult.Success -> {
                val streaming = result.data
                if (streaming != null) {
                    ApiResult.Success(streaming)
                } else {
                    streamSupport.messageUnsupported = true
                    Log.w(TAG, "서버에 스트리밍 엔드포인트가 없어 /message로 폴백 (이후 이 프로세스에서는 스트리밍 미사용)")
                    sendMessageBuffered(audio)
                }
            }
            is ApiResult.Error -> result
        }
    }

    /**
     * 스트리밍 요청을 열고 META와 첫 TEXT 프레임까지 읽는다 (`/start-stream`, `/message-stream` 공통).
     * @return 엔드포인트가 없으면(404/405) null
     * @throws HttpException 그 외 HTTP 오류 (safeApiCall이 Client/ServerError로 변환)
     * @throws IOException 네트워크 오류, 또는 첫머리 프레임을 읽지 못함
     */
    private suspend fun openStream(call: suspend () -> Response<ResponseBody>): MessageReply.Streaming? {
        val response = call()
        if (response.code() == 404 || response.code() == 405) {
            response.errorBody()?.close()
            return null
        }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw HttpException(response)
        }
        val body = response.body() ?: throw IOException("스트리밍 응답 본문이 비어 있습니다")

        // 본문 읽기는 블로킹 I/O라 메인 스레드에서 하면 안 된다
        return withContext(ioDispatcher) {
            val input = body.byteStream()
            try {
                val start = ConversationStreamProtocol.readStart(input)
                TurnLatencyTracker.onFirstFrame()
                MessageReply.Streaming(start.userMessage, start.firstText, AudioFrameInputStream(input))
            } catch (e: Throwable) {
                body.close()
                throw e
            }
        }
    }

    private suspend fun sendMessageBuffered(audio: MultipartBody.Part): ApiResult<MessageReply> {
        return when (val result = sendMessage(audio)) {
            is ApiResult.Success -> ApiResult.Success(
                MessageReply.Buffered(
                    userMessage = result.data.userMessage,
                    aiResponse = result.data.aiResponse,
                    audioData = result.data.audioData
                )
            )
            is ApiResult.Error -> result
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

    private companion object {
        const val TAG = "ConversationRepository"
    }
}
