package com.example.graduation_project.presentation.conversation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
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
class ConversationViewModel : ViewModel() {

    // 내부에서만 수정 가능한 상태
    private val _uiState = MutableStateFlow(ConversationUiState())

    // 외부에서 관찰만 가능한 상태 (읽기 전용)
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // Repository
    private val repository = ConversationRepository()

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
                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            isConversationActive = true,
                            sessionId = response.sessionId,
                            voiceStatus = VoiceStatus.PLAYING,
                            messages = currentState.messages + createAiMessage(
                                response.message ?: "안녕하세요! 오늘 하루는 어떠셨나요?"
                            )
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
     * 에러 메시지를 닫습니다.
     * - Snackbar 닫기 시 호출
     */
    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * 사용자 메시지를 추가합니다.
     * - 음성 인식 결과를 메시지로 추가할 때 사용
     */
    fun addUserMessage(text: String) {
        _uiState.update { currentState ->
            currentState.copy(
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

    // AI 메시지 객체 생성 헬퍼 함수
    private fun createAiMessage(text: String) = MessageUiModel(
        id = UUID.randomUUID().toString(),
        text = text,
        isFromUser = false,
        timestamp = System.currentTimeMillis()
    )

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
