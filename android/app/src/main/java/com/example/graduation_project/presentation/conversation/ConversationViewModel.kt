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
import com.example.graduation_project.data.voice.AudioPlayerManager
import com.example.graduation_project.domain.voice.AudioPlayException
import com.example.graduation_project.domain.voice.AudioPlayListener
import com.example.graduation_project.presentation.character.CharacterState
import com.example.graduation_project.presentation.model.ConversationUiState
import com.example.graduation_project.presentation.model.MessageUiModel
import com.example.graduation_project.presentation.model.PlaybackStatus
import com.example.graduation_project.presentation.model.SpeechErrorType
import com.example.graduation_project.presentation.model.VoiceStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

    // AI 응답 음성 재생 관리자
    private val audioPlayerManager = AudioPlayerManager()

    // PROCESSING 상태 타이머 Job
    private var processingTimerJob: Job? = null

    // 최대 사용자 재시도 횟수
    private companion object {
        const val MAX_USER_RETRY_COUNT = 3
        // 테스트 모드: true면 서버 없이 UI 테스트 가능
        const val TEST_MODE = false
    }

    init {
        setupAudioPlayListener()
    }

    private fun setupAudioPlayListener() {
        audioPlayerManager.setListener(object : AudioPlayListener {
            override fun onPlaybackStart() {
                // Preparing/Retrying → Playing 전환 (재시도 성공 시 폴백 숨김)
                _uiState.update {
                    it.copy(
                        playbackStatus = PlaybackStatus.PLAYING,
                        isAudioRetrying = false,
                        showAudioFallbackText = false,
                        retryProgress = null
                    )
                }
            }

            override fun onPlaybackComplete() {
                // 재생 완료 → LISTENING + playbackStatus 초기화
                _uiState.update {
                    it.copy(
                        voiceStatus = VoiceStatus.LISTENING,
                        playbackStatus = PlaybackStatus.NONE,
                        characterState = CharacterState.LISTENING,  // 캐릭터 상태 동기화
                        isAudioRetrying = false,
                        showAudioFallbackText = false,
                        retryProgress = null
                    )
                }
            }

            override fun onRetrying(currentAttempt: Int, maxAttempts: Int) {
                // 재시도 시작 → UI에 진행 상황 표시
                _uiState.update {
                    it.copy(
                        isAudioRetrying = true,
                        playbackStatus = PlaybackStatus.PREPARING,
                        retryProgress = "재시도 중 ($currentAttempt/$maxAttempts)"
                    )
                }
            }

            override fun onError(exception: AudioPlayException, isFallbackNeeded: Boolean) {
                // 마지막 AI 메시지 텍스트 추출 (폴백용)
                val lastAiMessage = _uiState.value.messages
                    .lastOrNull { !it.isFromUser }
                    ?.text

                _uiState.update {
                    it.copy(
                        voiceStatus = VoiceStatus.LISTENING,
                        playbackStatus = PlaybackStatus.NONE,
                        // TTS 에러 시 캐릭터 상태: 폴백 필요하면 TTS_ERROR, 아니면 LISTENING
                        characterState = if (isFallbackNeeded) CharacterState.TTS_ERROR else CharacterState.LISTENING,
                        isAudioRetrying = false,
                        showAudioFallbackText = isFallbackNeeded && lastAiMessage != null,
                        audioFallbackText = lastAiMessage,
                        retryProgress = null,
                        errorMessage = getAudioErrorMessage(exception, isFallbackNeeded)
                    )
                }
            }
        })
    }

    /**
     * 오디오 재생 에러 메시지 생성
     *
     * @param exception 발생한 예외
     * @param isFallbackNeeded 텍스트 폴백 필요 여부
     * @return 사용자에게 표시할 에러 메시지
     *
     * [T2.3-3] 재생 에러 처리
     */
    private fun getAudioErrorMessage(
        exception: AudioPlayException,
        isFallbackNeeded: Boolean
    ): String {
        return if (isFallbackNeeded) {
            // 텍스트 폴백 표시 시 긍정적 메시지
            "음성을 재생할 수 없어 텍스트로 보여드려요"
        } else {
            // 폴백 불가능 시 기존 에러 메시지
            exception.message ?: "음성 재생 오류"
        }
    }

    /**
     * 대화를 시작합니다.
     * 1. 로딩 상태로 변경
     * 2. 건강 데이터와 함께 API 호출
     * 3. 성공 시: 대화 활성화 + AI 메시지 추가
     * 4. 실패 시: 에러 메시지 표시
     */
    fun startConversation() {
        // 테스트 모드: 서버 없이 UI 테스트
        if (TEST_MODE) {
            startConversationTestMode()
            return
        }

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
                            playbackStatus = PlaybackStatus.PREPARING,
                            characterState = CharacterState.SPEAKING,  // 캐릭터 상태 동기화
                            messages = currentState.messages + aiMessage
                        )
                    }

                    // AI 응답 음성 재생
                    response.audioData?.let { audioData ->
                        audioPlayerManager.play(audioData)
                    } ?: run {
                        // audioData가 없으면 바로 LISTENING으로 전환
                        _uiState.update {
                            it.copy(
                                voiceStatus = VoiceStatus.LISTENING,
                                playbackStatus = PlaybackStatus.NONE,
                                characterState = CharacterState.LISTENING  // 캐릭터 상태 동기화
                            )
                        }
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
     * 테스트 모드: 서버 없이 UI 상태 전환 테스트
     * 상태 순환: IDLE → LISTENING → PROCESSING → SPEAKING → LISTENING (반복)
     */
    private fun startConversationTestMode() {
        viewModelScope.launch {
            conversationId = UUID.randomUUID().toString()
            val aiMessage = createAiMessage("안녕하세요! 테스트 모드입니다. 오늘 하루는 어떠셨나요?")

            // 1. 대화 시작 → IDLE 캐릭터 애니메이션
            _uiState.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    isConversationActive = true,
                    sessionId = conversationId,
                    voiceStatus = VoiceStatus.IDLE,
                    characterState = CharacterState.IDLE,
                    messages = currentState.messages + aiMessage
                )
            }

            delay(2000L)

            // 2. SPEAKING 상태 (AI가 말하는 중)
            _uiState.update {
                it.copy(
                    voiceStatus = VoiceStatus.PLAYING,
                    characterState = CharacterState.SPEAKING
                )
            }

            delay(3000L)

            // 3. LISTENING 상태 (사용자 음성 대기)
            _uiState.update {
                it.copy(
                    voiceStatus = VoiceStatus.LISTENING,
                    characterState = CharacterState.LISTENING
                )
            }

            delay(3000L)

            // 4. PROCESSING 상태 (서버 처리 중 - 타이머 테스트)
            transitionToCharacterState(CharacterState.PROCESSING)
            _uiState.update {
                it.copy(voiceStatus = VoiceStatus.IDLE)
            }

            // 8초 대기 (3초 후 "잠시만요", 6초 후 "조금만 기다려주세요" 확인)
            delay(8000L)

            // 5. 다시 SPEAKING
            _uiState.update {
                it.copy(
                    voiceStatus = VoiceStatus.PLAYING,
                    characterState = CharacterState.SPEAKING,
                    messages = it.messages + createAiMessage("처리가 완료되었습니다!")
                )
            }

            delay(3000L)

            // 6. LISTENING으로 복귀
            _uiState.update {
                it.copy(
                    voiceStatus = VoiceStatus.LISTENING,
                    characterState = CharacterState.LISTENING
                )
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
            // PROCESSING 상태로 전환 + 타이머 시작
            transitionToCharacterState(CharacterState.PROCESSING)
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    voiceStatus = VoiceStatus.IDLE,
                    playbackStatus = PlaybackStatus.NONE,
                    // [T2.3-3] 폴백 텍스트 숨김 (새 음성 입력 시작)
                    showAudioFallbackText = false,
                    audioFallbackText = null,
                    retryProgress = null
                )
            }

            // WAV ByteArray → MultipartBody.Part 변환
            val requestBody = wavData.toRequestBody("audio/wav".toMediaType())
            val audioPart = MultipartBody.Part.createFormData("audio", "recording.wav", requestBody)

            val result = repository.sendMessage(audioPart)

            // PROCESSING 타이머 중지
            stopProcessingTimer()

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    val userMessage = createUserMessage(response.userMessage ?: "")
                    val aiMessage = createAiMessage(response.aiResponse ?: "")

                    _uiState.update { currentState ->
                        currentState.copy(
                            isLoading = false,
                            voiceStatus = VoiceStatus.PLAYING,
                            playbackStatus = PlaybackStatus.PREPARING,
                            characterState = CharacterState.SPEAKING,  // 캐릭터 상태 동기화
                            messages = currentState.messages + userMessage + aiMessage
                        )
                    }

                    // AI 응답 음성 재생
                    response.audioData?.let { audioData ->
                        audioPlayerManager.play(audioData)
                    } ?: run {
                        // audioData가 없으면 바로 LISTENING으로 전환
                        _uiState.update {
                            it.copy(
                                voiceStatus = VoiceStatus.LISTENING,
                                playbackStatus = PlaybackStatus.NONE,
                                characterState = CharacterState.LISTENING  // 캐릭터 상태 동기화
                            )
                        }
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
                            characterState = CharacterState.LISTENING,  // 캐릭터 상태 동기화
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
                    audioPlayerManager.stop()  // 재시도 중이어도 즉시 중지
                    stopProcessingTimer()  // PROCESSING 타이머 중지
                    conversationId = null
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isConversationActive = false,
                            sessionId = null,
                            voiceStatus = VoiceStatus.IDLE,
                            playbackStatus = PlaybackStatus.NONE,
                            characterState = CharacterState.IDLE,  // 캐릭터 상태 초기화
                            // [T2.3-3] 폴백 관련 상태 초기화
                            isAudioRetrying = false,
                            showAudioFallbackText = false,
                            audioFallbackText = null,
                            retryProgress = null,
                            // 캐릭터 관련 상태 초기화
                            processingMessage = null,
                            processingElapsedSeconds = 0,
                            speechErrorMessage = null,
                            speechErrorHint = null,
                            speechFailCount = 0,
                            userRetryCount = 0,
                            isRetryButtonEnabled = false,
                            showContactSupport = false,
                            showFarewellDialog = false
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

    override fun onCleared() {
        super.onCleared()
        audioPlayerManager.release()
    }

    // 임시 건강 데이터 (추후 Health Connect 연동)
    private fun getDummyHealthData() = HealthData(
        sleepDuration = 420,      // 7시간 (분 단위)
        steps = 5000,             // 5000보
        exerciseDistance = 3.5,   // 3.5km
        exerciseActivity = "걷기"
    )

    // ═══════════════════════════════════════════════════════════════════
    // 캐릭터 상태 관리
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 캐릭터 상태를 전환합니다.
     * VoiceStatus와 동기화하여 적절한 CharacterState로 매핑
     */
    fun transitionToCharacterState(state: CharacterState) {
        // 이전 PROCESSING 타이머 정리
        if (state != CharacterState.PROCESSING) {
            stopProcessingTimer()
        }

        _uiState.update {
            it.copy(
                characterState = state,
                // 상태 전환 시 관련 필드 초기화
                processingMessage = null,
                processingElapsedSeconds = 0,
                speechErrorMessage = null,
                speechErrorHint = null
            )
        }

        // PROCESSING 상태면 타이머 시작
        if (state == CharacterState.PROCESSING) {
            startProcessingTimer()
        }
    }

    /**
     * VoiceStatus 변경에 따른 CharacterState 동기화
     */
    private fun syncCharacterStateWithVoiceStatus(voiceStatus: VoiceStatus) {
        val characterState = when (voiceStatus) {
            VoiceStatus.IDLE -> CharacterState.IDLE
            VoiceStatus.LISTENING -> CharacterState.LISTENING
            VoiceStatus.RECORDING -> CharacterState.LISTENING // RECORDING도 LISTENING 영상 사용
            VoiceStatus.PLAYING -> CharacterState.SPEAKING
        }
        transitionToCharacterState(characterState)
    }

    // ═══════════════════════════════════════════════════════════════════
    // PROCESSING 타이머
    // ═══════════════════════════════════════════════════════════════════

    /**
     * PROCESSING 상태 타이머를 시작합니다.
     * - 0~3초: 오버레이 없음
     * - 3~6초: "잠시만요"
     * - 6초~: "조금만 기다려주세요"
     */
    private fun startProcessingTimer() {
        stopProcessingTimer()
        processingTimerJob = viewModelScope.launch {
            var seconds = 0
            while (true) {
                delay(1000L)
                seconds++
                val message = when {
                    seconds < 3 -> null
                    seconds < 6 -> "잠시만요"
                    else -> "조금만 기다려주세요"
                }
                _uiState.update {
                    it.copy(
                        processingElapsedSeconds = seconds,
                        processingMessage = message
                    )
                }
            }
        }
    }

    /**
     * PROCESSING 상태 타이머를 중지합니다.
     */
    private fun stopProcessingTimer() {
        processingTimerJob?.cancel()
        processingTimerJob = null
        _uiState.update {
            it.copy(
                processingElapsedSeconds = 0,
                processingMessage = null
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 발화 인식 오류 처리
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 발화 인식 실패 시 호출
     * 연속 실패 횟수에 따라 다른 힌트 제공
     *
     * @param errorType 오류 타입
     */
    fun onSpeechUnrecognized(errorType: SpeechErrorType) {
        val currentFailCount = _uiState.value.speechFailCount + 1
        val (message, hint) = resolveHint(errorType, currentFailCount)

        _uiState.update {
            it.copy(
                characterState = CharacterState.SPEECH_UNRECOGNIZED,
                speechErrorMessage = message,
                speechErrorHint = hint,
                speechFailCount = currentFailCount
            )
        }

        // 2초 후 자동으로 LISTENING으로 복귀
        viewModelScope.launch {
            delay(2000L)
            if (_uiState.value.characterState == CharacterState.SPEECH_UNRECOGNIZED) {
                _uiState.update {
                    it.copy(
                        characterState = CharacterState.LISTENING,
                        voiceStatus = VoiceStatus.LISTENING,
                        speechErrorMessage = null,
                        speechErrorHint = null
                    )
                }
            }
        }
    }

    /**
     * 발화 인식 실패 횟수에 따른 힌트 생성
     */
    private fun resolveHint(errorType: SpeechErrorType, failCount: Int): Pair<String, String> {
        val message = when (errorType) {
            SpeechErrorType.NOT_DETECTED -> "말씀이 잘 들리지 않았어요"
            SpeechErrorType.TOO_SHORT -> "조금 더 길게 말씀해 주세요"
            SpeechErrorType.STT_FAILED -> "다시 한번 말씀해 주세요"
        }

        val hint = when {
            failCount >= 3 -> "마이크에 가까이 대고 천천히 말씀해 주세요"
            failCount >= 2 -> "조용한 곳에서 다시 시도해 주세요"
            else -> "천천히, 또박또박 말씀해 주세요"
        }

        return message to hint
    }

    /**
     * 발화 인식 성공 시 실패 카운트 초기화
     */
    fun onSpeechRecognized() {
        _uiState.update {
            it.copy(speechFailCount = 0)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 네트워크/서버 오류 처리
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 네트워크 오류 발생 시 호출
     */
    fun onNetworkError() {
        stopProcessingTimer()
        _uiState.update {
            it.copy(
                characterState = CharacterState.NETWORK_ERROR,
                isRetryButtonEnabled = it.userRetryCount < MAX_USER_RETRY_COUNT,
                showContactSupport = it.userRetryCount >= MAX_USER_RETRY_COUNT,
                errorMessage = "인터넷 연결을 확인해주세요"
            )
        }
    }

    /**
     * 서버 오류 발생 시 호출
     */
    fun onServerError() {
        stopProcessingTimer()
        _uiState.update {
            it.copy(
                characterState = CharacterState.SERVER_ERROR,
                isRetryButtonEnabled = it.userRetryCount < MAX_USER_RETRY_COUNT,
                showContactSupport = it.userRetryCount >= MAX_USER_RETRY_COUNT,
                errorMessage = "서버에 문제가 생겼습니다. 잠시 후 다시 시도해주세요"
            )
        }
    }

    /**
     * 사용자 재시도 버튼 클릭 시 호출
     */
    fun onUserRetryClicked() {
        val newRetryCount = _uiState.value.userRetryCount + 1
        _uiState.update {
            it.copy(
                userRetryCount = newRetryCount,
                isRetryButtonEnabled = newRetryCount < MAX_USER_RETRY_COUNT,
                showContactSupport = newRetryCount >= MAX_USER_RETRY_COUNT,
                characterState = CharacterState.PROCESSING,
                errorMessage = null
            )
        }
        startProcessingTimer()
    }

    /**
     * 재시도 관련 상태 초기화
     */
    private fun resetRetryState() {
        _uiState.update {
            it.copy(
                userRetryCount = 0,
                isRetryButtonEnabled = false,
                showContactSupport = false
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 종료 흐름 (Farewell)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 종료 버튼 클릭 시 다이얼로그 표시
     */
    fun onFarewellButtonClicked() {
        _uiState.update { it.copy(showFarewellDialog = true) }
    }

    /**
     * 종료 확인 시 FAREWELL 애니메이션 재생
     */
    fun onFarewellConfirmed() {
        _uiState.update {
            it.copy(
                showFarewellDialog = false,
                characterState = CharacterState.FAREWELL
            )
        }
        // FAREWELL 애니메이션 완료 후 endConversation() 호출은
        // CharacterAnimationManager.onFarewellFinished 콜백으로 처리
    }

    /**
     * 종료 취소 시 다이얼로그 닫기
     */
    fun onFarewellCancelled() {
        _uiState.update { it.copy(showFarewellDialog = false) }
    }

    /**
     * FAREWELL 애니메이션 완료 후 호출
     * 대화 종료 처리
     */
    fun onFarewellAnimationFinished() {
        endConversation()
    }
}
