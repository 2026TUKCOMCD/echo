package com.example.graduation_project.presentation.conversation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.AppDatabase
import com.example.graduation_project.data.local.entity.MessageEntity
import com.example.graduation_project.data.model.HealthData
import com.example.graduation_project.data.repository.ConversationRepository
import com.example.graduation_project.presentation.model.ConversationUiState
import com.example.graduation_project.presentation.model.MessageUiModel
import com.example.graduation_project.presentation.model.VoiceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

/**
 * 대화 화면의 상태를 관리하는 ViewModel
 *
 * ## 주요 개념
 * - StateFlow: 상태를 관찰할 수 있는 데이터 스트림
 * - update { }: 현재 상태를 기반으로 새 상태를 만드는 함수
 * - viewModelScope: ViewModel이 살아있는 동안 실행되는 코루틴 스코프
 *
 * ## 상태 흐름
 * 1. 대화 시작 버튼 클릭 -> startConversation() 호출
 * 2. API 호출 성공 -> isConversationActive = true, AI 메시지 추가
 * 3. 음성 상태 변화 -> voiceStatus 업데이트
 * 4. 대화 종료 버튼 클릭 -> endConversation() 호출
 * 5. 상태 초기화
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    // 내부에서만 수정 가능한 상태
    private val _uiState = MutableStateFlow(ConversationUiState())

    // 외부에서 관찰만 가능한 상태 (읽기 전용)
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // Repository
    private val repository = ConversationRepository()

    // Room DB
    private val messageDao = AppDatabase.getInstance(application).messageDao()

    // 로컬 대화 세션 ID (대화 시작 시 생성, 종료 시 초기화)
    private var conversationId: String? = null

    /**
     * 대화를 시작합니다.
     * 1. 로딩 상태로 변경
     * 2. 건강 데이터와 함께 API 호출
     * 3. 성공 시: 대화 활성화 + AI 메시지 추가
     * 4. 실패 시: 에러 메시지 표시
     */
    fun startConversation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val result = repository.startConversation(getDummyHealthData())

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    conversationId = UUID.randomUUID().toString()
                    val aiMessage = createAiMessage(
                        response.message ?: "안녕하세요! 오늘 하루는 어떠셨나요?"
                    )

                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            isConversationActive = true,
                            sessionId = conversationId,
                            voiceStatus = VoiceStatus.PLAYING,
                            messages = currentState.messages + aiMessage
                            // TODO: response.audioData (Base64) 디코딩 후 TTS 재생
                        )
                    }

                    // AI 인사 메시지 Room DB 저장
                    saveMessageToDb(aiMessage)
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = getErrorMessage(result.exception)
                        )
                    }
                }
            }
        }
    }

    /**
     * 녹음된 음성을 서버에 전송합니다.
     * 1. WAV ByteArray → MultipartBody.Part 변환
     * 2. Repository를 통해 서버에 업로드
     * 3. 성공 시: 사용자 메시지(STT) + AI 응답 메시지 추가
     * 4. 실패 시: 에러 메시지 표시
     *
     * @param wavData VoiceRecordingViewModel에서 전달받은 WAV 바이너리 데이터
     */
    fun sendMessage(wavData: ByteArray) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, voiceStatus = VoiceStatus.IDLE)
            }

            // WAV ByteArray → MultipartBody.Part 변환
            val requestBody = wavData.toRequestBody("audio/wav".toMediaType())
            val audioPart = MultipartBody.Part.createFormData("audio", "recording.wav", requestBody)

            val result = repository.sendMessage(audioPart)

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    val userMessage = createUserMessage(response.userMessage ?: "")
                    val aiMessage = createAiMessage(response.aiResponse ?: "")

                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            voiceStatus = VoiceStatus.PLAYING,
                            messages = currentState.messages + userMessage + aiMessage
                            // TODO: response.audioData (Base64) 디코딩 후 TTS 재생
                        )
                    }

                    // 사용자 메시지 + AI 응답 메시지 Room DB 저장
                    saveMessageToDb(userMessage)
                    saveMessageToDb(aiMessage)
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            voiceStatus = VoiceStatus.LISTENING,
                            errorMessage = getErrorMessage(result.exception)
                        )
                    }
                }
            }
        }
    }

    /**
     * 대화 시작을 재시도합니다.
     * - 에러 발생 후 재시도 버튼 클릭 시 호출
     */
    fun retryStartConversation() {
        dismissError()
        startConversation()
    }

    /**
     * 대화를 종료합니다.
     * 1. 로딩 상태로 변경
     * 2. 종료 API 호출
     * 3. 상태 초기화 (메시지는 유지)
     */
    fun endConversation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = repository.endConversation()

            when (result) {
                is ApiResult.Success -> {
                    conversationId = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isConversationActive = false,
                            sessionId = null,
                            voiceStatus = VoiceStatus.IDLE
                            // messages는 유지 (대화 기록 보존)
                        )
                    }
                }

                is ApiResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = getErrorMessage(result.exception)
                        )
                    }
                }
            }
        }
    }

    /**
     * 음성 상태를 업데이트합니다.
     * - 음성 녹음/재생 상태에 따라 호출
     */
    fun updateVoiceStatus(status: VoiceStatus) {
        _uiState.update { it.copy(voiceStatus = status) }
    }

    /**
     * 음성 볼륨(amplitude)을 업데이트합니다.
     * - 마이크 입력의 볼륨을 실시간으로 전달
     * - 이퀄라이저 애니메이션에 반영됨
     * @param amplitude 0.0 ~ 1.0 사이의 볼륨 값
     */
    fun updateVoiceAmplitude(amplitude: Float) {
        _uiState.update { it.copy(voiceAmplitude = amplitude.coerceIn(0f, 1f)) }
    }

    /**
     * 에러 메시지를 닫습니다.
     * - Snackbar 닫기 시 호출
     */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 실시간 음성 인식 텍스트를 업데이트합니다.
     * - 음성 인식 중 부분 결과를 화면에 표시할 때 사용
     */
    fun updateCurrentUserSpeech(text: String?) {
        _uiState.update { it.copy(currentUserSpeech = text) }
    }

    /**
     * 사용자 메시지를 추가합니다.
     * - 음성 인식 결과를 메시지로 추가할 때 사용
     * - 실시간 텍스트를 초기화하고 최종 메시지로 추가
     */
    fun addUserMessage(text: String) {
        _uiState.update { currentState ->
            currentState.copy(
                currentUserSpeech = null,
                messages = currentState.messages + MessageUiModel(
                    id = UUID.randomUUID().toString(),
                    text = text,
                    isFromUser = true,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * AI 메시지를 추가합니다.
     * - API 응답을 메시지로 추가할 때 사용
     */
    fun addAiMessage(text: String) {
        _uiState.update { currentState ->
            currentState.copy(
                messages = currentState.messages + createAiMessage(text)
            )
        }
    }

    // 사용자 메시지 객체 생성 헬퍼 함수
    private fun createUserMessage(text: String) = MessageUiModel(
        id = UUID.randomUUID().toString(),
        text = text,
        isFromUser = true,
        timestamp = System.currentTimeMillis()
    )

    // AI 메시지 객체 생성 헬퍼 함수
    private fun createAiMessage(text: String) = MessageUiModel(
        id = UUID.randomUUID().toString(),
        text = text,
        isFromUser = false,
        timestamp = System.currentTimeMillis()
    )

    // 메시지를 Room DB에 저장하는 헬퍼 함수
    private fun saveMessageToDb(message: MessageUiModel) {
        val convId = conversationId ?: return
        viewModelScope.launch {
            messageDao.insertMessage(
                MessageEntity(
                    id = message.id,
                    conversationId = convId,
                    role = if (message.isFromUser) MessageEntity.ROLE_USER else MessageEntity.ROLE_ASSISTANT,
                    content = message.text,
                    timestamp = message.timestamp
                )
            )
        }
    }

    // 에러 타입별 사용자 친화적 메시지
    private fun getErrorMessage(exception: ApiException): String = when (exception) {
        is ApiException.NetworkError -> "인터넷 연결을 확인해주세요"
        is ApiException.ServerError -> "서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요"
        is ApiException.ClientError -> "요청에 문제가 있습니다"
        is ApiException.UnknownError -> "알 수 없는 오류가 발생했습니다"
    }

    // 임시 건강 데이터 (추후 Health Connect 연동)
    private fun getDummyHealthData() = HealthData(
        sleepDuration = 420,      // 7시간 (분 단위)
        steps = 5000,             // 5000보
        exerciseDistance = 3.5,   // 3.5km
        exerciseActivity = "걷기"
    )
}
