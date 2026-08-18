package com.example.graduation_project.presentation.conversation

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.graduation_project.data.alarm.ConversationAlarmReceiver
import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.health.HealthConnectManager
import com.example.graduation_project.data.health.HealthConnectRepositoryImpl
import com.example.graduation_project.data.location.LocationDataManager
import com.example.graduation_project.data.location.LocationManager
import com.example.graduation_project.data.location.LocationStorageManager
import com.example.graduation_project.domain.health.IHealthRepository
import com.example.graduation_project.data.health.StayPointDetectorImpl
import com.example.graduation_project.data.local.AppDatabase
import com.example.graduation_project.data.local.dao.MessageDao
import com.example.graduation_project.data.local.entity.ConversationDiaryLinkEntity
import com.example.graduation_project.data.local.entity.MessageEntity
import com.example.graduation_project.data.repository.ConversationRepository
import com.example.graduation_project.data.repository.DiaryRepository
import com.example.graduation_project.domain.usecase.GetHealthDataUseCase
import com.example.graduation_project.data.voice.AudioPlayerManager
import com.example.graduation_project.data.voice.AudioRecordManager
import com.example.graduation_project.domain.voice.AudioPlayException
import com.example.graduation_project.domain.voice.AudioPlayListener
import com.example.graduation_project.domain.voice.AudioRecordException
import com.example.graduation_project.domain.voice.AudioRecordListener
import com.example.graduation_project.domain.voice.AudioRecordState
import com.example.graduation_project.presentation.model.ConversationError
import com.example.graduation_project.presentation.model.ConversationState
import com.example.graduation_project.presentation.model.ConversationUiState
import com.example.graduation_project.presentation.model.MessageUiModel
import com.example.graduation_project.presentation.model.PlaybackStatus
import com.example.graduation_project.presentation.model.SpeechErrorType
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import java.io.File
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
 * 1.
 * 대화 시작 버튼 클릭 -> startConversation() 호출
 * 2. API 호출 성공 -> isConversationActive = true, AI 메시지 추가
 * 3. 음성 상태 변화 -> conversationState 업데이트
 * 4. 대화 종료 버튼 클릭 -> endConversation() 호출
 * 5. 상태 초기화
 */
class ConversationViewModel(
    application: Application,
    private val repository: ConversationRepository = ConversationRepository(),
    private val messageDao: MessageDao = AppDatabase.getInstance(application).messageDao(),
    private val audioRecordManager: AudioRecordManager = AudioRecordManager(application),
    private val healthRepository: IHealthRepository =
        HealthConnectRepositoryImpl(HealthConnectManager(application)),
    private val locationDataManager: LocationDataManager = LocationDataManager(
        context = application,
        locationManager = LocationManager(application),
        locationStorageManager = LocationStorageManager(
            AppDatabase.getInstance(application).locationPointDao()
        ),
        stayPointDetector = StayPointDetectorImpl()
    ),
    private val audioPlayerManager: AudioPlayerManager = AudioPlayerManager()
) : AndroidViewModel(application) {

    private val getHealthDataUseCase = GetHealthDataUseCase(healthRepository)

    // 로컬 메시지 캐시 격리용 - 계정 전환 시 다른 계정의 대화가 섞이지 않도록 함
    private val currentUserId: Long = ApiClient.tokenStorage?.getCurrentUserId() ?: -1L

    // 내부에서만 수정 가능한 상태
    private val _uiState = MutableStateFlow(ConversationUiState())

    // 외부에서 관찰만 가능한 상태 (읽기 전용)
    val uiState: StateFlow<ConversationUiState> = _uiState.asStateFlow()

    // 로컬 대화 세션 ID (대화 시작 시 생성, 종료 시 초기화)
    private var conversationId: String? = null

    // PROCESSING 상태 타이머 Job
    private var processingTimerJob: Job? = null


    // 서버 TTS 재요청 진행 중 여부 (무한 루프 방지)
    private var isServerRetryInProgress = false

    // 녹음 오류 자동 복구 시도 횟수 (연속 실패 시 무한 재시도 방지)
    private var recorderRecoveryAttempts = 0
    private val MAX_RECORDER_RECOVERY_ATTEMPTS = 3

    // 백그라운드 전환으로 오디오를 중지했는지 여부 (복귀 시 재개 판단용)
    private var wasAudioStoppedForBackground = false

    // [TEST ONLY] true로 설정하면 음성 재생 시 강제로 DecodeError 발생 → 서버 TTS 재요청 흐름 테스트
    private val forceDecodeErrorForTest = false

    // 최대 사용자 재시도 횟수
    private val MAX_USER_RETRY_COUNT = 3


    init {
        setupAudioPlayListener()
        setupAudioRecordListener()
        observeAudioRecordState()
    }

    /**
     * 음성 녹음 리스너 설정
     * VAD 상태 변화에 따라 isSpeechDetected 업데이트 및 ConversationState 전환
     */
    private fun setupAudioRecordListener() {
        audioRecordManager.setListener(object : AudioRecordListener {
            override fun onReady() {
                // VAD 준비 완료 → Listening 상태로 전환
                Log.d(TAG, "AudioRecordListener.onReady()")
            }

            override fun onRecordingStart() {
                // VAD가 음성 감지 - Listening 상태에서만 Recording으로 전환
                // (Playing 상태에서 VAD 이벤트 무시)
                val currentState = _uiState.value.conversationState
                if (currentState !is ConversationState.Listening) {
                    Log.d(TAG, "AudioRecordListener.onRecordingStart() - 무시됨 (현재 상태: $currentState)")
                    return
                }
                Log.d(TAG, "AudioRecordListener.onRecordingStart() - 음성 감지됨")
                recorderRecoveryAttempts = 0  // 마이크 정상 동작 확인 → 복구 카운터 리셋
                _uiState.update { it.copy(isSpeechDetected = true) }
                transitionTo(ConversationState.Recording)
            }

            override fun onRecordingComplete(audioFile: File) {
                // 녹음 완료 → 서버 전송 (Recording 상태에서만)
                val currentState = _uiState.value.conversationState
                if (currentState !is ConversationState.Recording) {
                    Log.d(TAG, "AudioRecordListener.onRecordingComplete() - 무시됨 (현재 상태: $currentState)")
                    return
                }
                Log.d(TAG, "AudioRecordListener.onRecordingComplete() - 파일: ${audioFile.path}")
                _uiState.update { it.copy(isSpeechDetected = false) }
                sendMessage(audioFile.readBytes())
            }

            override fun onError(exception: AudioRecordException) {
                Log.e(TAG, "AudioRecordListener.onError()", exception)
                _uiState.update { it.copy(isSpeechDetected = false, isRecordingPreparing = false) }
                // 마이크가 죽은 채 Listening 화면만 남는 것 방지 → 자동 복구 시도
                recoverRecordingAfterError()
            }
        })
    }

    /**
     * 녹음 오류 후 자동 복구를 시도합니다.
     * - Listening/Recording 상태에서 녹음이 죽으면 잠시 후 다시 발화 대기를 시작
     * - 연속 실패가 누적되면(권한 회수 등 복구 불가 상황) 사용자에게 안내하고 중단
     */
    private fun recoverRecordingAfterError() {
        // 백그라운드 전환으로 의도적으로 녹음을 중지한 경우에는 복구하지 않음
        // (stop() 호출 시에도 VAD 코루틴 취소가 onError로 전파되기 때문)
        if (wasAudioStoppedForBackground) return

        val state = _uiState.value.conversationState
        if (state !is ConversationState.Listening && state !is ConversationState.Recording) return

        if (recorderRecoveryAttempts >= MAX_RECORDER_RECOVERY_ATTEMPTS) {
            Log.w(TAG, "녹음 복구 시도 초과 - 사용자 안내")
            _uiState.update {
                it.copy(errorMessage = "마이크를 사용할 수 없어요. 마이크 권한을 확인해주세요.")
            }
            return
        }

        recorderRecoveryAttempts++
        viewModelScope.launch {
            delay(RECORDER_RECOVERY_DELAY_MS)
            // Recording 상태에서 죽었으면 발화 대기 상태로 되돌린 후 재시작
            if (_uiState.value.conversationState is ConversationState.Recording) {
                transitionTo(ConversationState.Listening)
            }
            if (_uiState.value.conversationState is ConversationState.Listening) {
                Log.d(TAG, "녹음 자동 복구 시도 ($recorderRecoveryAttempts/$MAX_RECORDER_RECOVERY_ATTEMPTS)")
                audioRecordManager.resumeListening()
            }
        }
    }

    /**
     * AudioRecordManager 상태 변화를 관찰하여 isRecordingPreparing 업데이트
     * - Preparing 상태: isRecordingPreparing = true (어르신 혼란 방지)
     * - 그 외 상태: isRecordingPreparing = false
     */
    private fun observeAudioRecordState() {
        viewModelScope.launch {
            audioRecordManager.state.collect { state ->
                val isPreparing = state is AudioRecordState.Preparing
                _uiState.update { it.copy(isRecordingPreparing = isPreparing) }
            }
        }
    }

    private fun setupAudioPlayListener() {
        audioPlayerManager.setListener(object : AudioPlayListener {
            override fun onPlaybackStart() {
                // TTS 재생 시작 시 VAD 중지 (스피커 소리 감지 방지)
                stopRecording()

                // Preparing/Retrying → Playing 전환 (재시도 성공 시 폴백 숨김)
                // 주의: isServerRetryInProgress는 여기서 리셋하지 않음.
                // 재생이 시작된 후 중간에 실패하는 경우(잘린 스트림 등)에도 서버 재요청은
                // 1회로 제한되어야 하므로, 완주(onPlaybackComplete) 시에만 리셋한다.
                _uiState.update {
                    it.copy(
                        playbackStatus = PlaybackStatus.PLAYING,
                        currentError = null,
                        isAudioRetrying = false,
                        showAudioFallbackText = false,
                        retryProgress = null
                    )
                }
            }

            override fun onPlaybackComplete() {
                // 재생 완료 → playbackStatus 초기화 (오디오 자체는 이미 끝났으므로 즉시 반영)
                isServerRetryInProgress = false
                _uiState.update {
                    it.copy(
                        playbackStatus = PlaybackStatus.NONE,
                        currentError = null,
                        isAudioRetrying = false,
                        showAudioFallbackText = false,
                        retryProgress = null,
                        isSpeechDetected = false
                    )
                }
                // LISTENING 전환 + 다음 발화 대기 시작은 자연스러운 턴 전환 여백을 두고 진행.
                // 그 사이 endConversation() 등으로 상태가 바뀌었으면(더 이상 Playing이 아니면)
                // 건너뛴다 - 종료 중인데 뒤늦게 LISTENING으로 되돌리고 마이크를 켜면 안 되므로.
                viewModelScope.launch {
                    delay(LISTENING_TRANSITION_DELAY_MS)
                    if (_uiState.value.conversationState is ConversationState.Playing) {
                        transitionTo(ConversationState.Listening)
                        startRecording()
                    }
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
                if (!isServerRetryInProgress) {
                    // 서버 TTS 재요청 시도 (DecodeError: 바로 진입 / PlaybackError: 로컬 재시도 소진 후 진입)
                    isServerRetryInProgress = true
                    requestServerTtsRetry()
                } else {
                    // 서버 재요청 후에도 실패 → 텍스트 폴백 (최후 수단)
                    isServerRetryInProgress = false
                    showTextFallback()
                }
            }
        })
    }

    /**
     * AI 응답 음성을 재생합니다.
     * - 재생 시작 전에 녹음을 먼저 중지해 재생 첫 부분이 깨지는 것을 방지
     *   (활성 녹음 세션이 있는 상태로 재생을 시작하면 실기기에서 오디오
     *   라우팅/에코 제거 경로 전환으로 첫 수백 ms가 깨져서 들림)
     */
    private fun playAiAudio(audioData: String) {
        stopRecording()
        audioPlayerManager.forceDecodeErrorForTest = forceDecodeErrorForTest
        audioPlayerManager.play(audioData)
    }

    /**
     * 서버에 TTS 재생성을 요청합니다.
     * - 로컬 재시도 소진 또는 DecodeError 발생 시 호출
     * - 서버의 마지막 AI 응답 텍스트를 TTS로 재생성하여 반환
     */
    private fun requestServerTtsRetry() {
        _uiState.update {
            it.copy(
                isAudioRetrying = true,
                playbackStatus = PlaybackStatus.PREPARING,
                retryProgress = "음성을 다시 불러오는 중..."
            )
        }
        viewModelScope.launch {
            when (val result = repository.retryTts()) {
                is ApiResult.Success -> {
                    val audioData = result.data.audioData
                    if (audioData != null) {
                        playAiAudio(audioData)
                    } else {
                        isServerRetryInProgress = false
                        showTextFallback()
                    }
                }
                is ApiResult.Error -> {
                    isServerRetryInProgress = false
                    showTextFallback()
                }
            }
        }
    }

    /**
     * 텍스트 폴백을 표시합니다.
     * - 서버 TTS 재요청도 실패했을 때 최후 수단으로 호출
     */
    private fun showTextFallback() {
        val lastAiMessage = _uiState.value.messages
            .lastOrNull { !it.isFromUser }
            ?.text

        transitionTo(ConversationState.Listening)
        _uiState.update {
            it.copy(
                playbackStatus = PlaybackStatus.NONE,
                currentError = ConversationError.TtsError,
                isAudioRetrying = false,
                showAudioFallbackText = lastAiMessage != null,
                audioFallbackText = lastAiMessage,
                retryProgress = null,
                errorMessage = "음성을 재생할 수 없어 텍스트로 보여드려요",
                isSpeechDetected = false
            )
        }
        // 다음 발화 대기 시작
        startRecording()
    }

    /**
     * 대화를 시작합니다.
     * 1. 로딩 상태로 변경
     * 2. 건강 데이터와 함께 API 호출
     * 3. 성공 시: 대화 활성화 + AI 메시지 추가
     * 4. 실패 시: 에러 메시지 표시
     */
    fun startConversation() {
        // Ended 상태에서 시작 버튼 클릭 시 먼저 Idle로 초기화
        if (_uiState.value.conversationState is ConversationState.Ended) {
            resetToIdle()
        }
        // 이미 대화가 진행 중이면(Listening/Recording/Sending/Playing 등) 재시작하지 않음.
        // canTransitionTo(Sending)만으로는 endConversation()/sendMessage()를 위해 열어둔
        // Listening/Recording → Sending 전이까지 통과시켜버려 재진입을 막지 못함.
        if (_uiState.value.conversationState !is ConversationState.Idle) return
        viewModelScope.launch {
            isServerRetryInProgress = false
            // Idle → Sending (이미 Sending이면 중복 요청으로 간주하고 차단)
            if (!transitionTo(ConversationState.Sending)) return@launch
            _uiState.update { it.copy(errorMessage = null, currentError = null) }

            // 대화 시간 알림 취소
            ConversationAlarmReceiver.cancelNotification(getApplication())

            // [A11] 위치 데이터 수집 (Room DB 기반 StayPoint 계산)
            _uiState.update { it.copy(processingMessage = "위치 데이터 수집 중") }
            val locationData = locationDataManager.collectLocationData()

            // [A11] 건강 데이터 수집
            _uiState.update { it.copy(processingMessage = "건강 데이터 수집 중") }
            val healthData = getHealthDataUseCase()

            // API 호출 대기 타이머
            _uiState.update { it.copy(processingMessage = null) }
            startProcessingTimer()
            val result = repository.startConversation(healthData, locationData)

            // PROCESSING 타이머 중지
            stopProcessingTimer()

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    conversationId = UUID.randomUUID().toString()
                    val aiMessage = createAiMessage(
                        response.message ?: "안녕하세요! 오늘 하루는 어떠셨나요?"
                    )

                    // Sending → Playing
                    transitionTo(ConversationState.Playing)
                    _uiState.update { currentState ->
                        currentState.copy(
                            sessionId = conversationId,
                            playbackStatus = PlaybackStatus.PREPARING,
                            messages = currentState.messages + aiMessage
                        )
                    }

                    // AI 응답 음성 재생 (재생 전 녹음 중지 포함)
                    response.audioData?.let { audioData ->
                        playAiAudio(audioData)
                    } ?: run {
                        // audioData가 없으면 바로 LISTENING으로 전환 + 녹음 시작
                        transitionTo(ConversationState.Listening)
                        _uiState.update {
                            it.copy(playbackStatus = PlaybackStatus.NONE, isSpeechDetected = false)
                        }
                        startRecording()
                    }

                    // AI 인사 메시지 Room DB 저장
                    saveMessageToDb(aiMessage)
                }

                is ApiResult.Error -> {
                    // Sending → Idle
                    transitionTo(ConversationState.Idle)
                    handleApiError(result.exception)
                    _uiState.update { it.copy(startFailed = true) }
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
            isServerRetryInProgress = false
            // Recording → Sending (이미 Sending이면 중복 요청으로 간주하고 차단)
            if (!transitionTo(ConversationState.Sending)) return@launch
            _uiState.update {
                it.copy(
                    errorMessage = null,
                    currentError = null,
                    // [T2.3-3] 폴백 텍스트 숨김 (새 음성 입력 시작)
                    showAudioFallbackText = false,
                    audioFallbackText = null,
                    retryProgress = null
                )
            }

            // PROCESSING 타이머 시작
            startProcessingTimer()

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

                    // 발화 인식 성공 → 실패 카운트 초기화
                    onSpeechRecognized()

                    // Sending → Playing
                    transitionTo(ConversationState.Playing)
                    _uiState.update { currentState ->
                        currentState.copy(
                            playbackStatus = PlaybackStatus.PREPARING,
                            messages = currentState.messages + userMessage + aiMessage
                        )
                    }

                    // AI 응답 음성 재생 (재생 전 녹음 중지 포함)
                    response.audioData?.let { audioData ->
                        playAiAudio(audioData)
                    } ?: run {
                        // audioData가 없으면 바로 LISTENING으로 전환 + 녹음 시작
                        transitionTo(ConversationState.Listening)
                        _uiState.update {
                            it.copy(playbackStatus = PlaybackStatus.NONE, isSpeechDetected = false)
                        }
                        startRecording()
                    }

                    // 사용자 메시지 + AI 응답 메시지 Room DB 저장
                    saveMessageToDb(userMessage)
                    saveMessageToDb(aiMessage)
                }

                is ApiResult.Error -> {
                    // Sending → Listening
                    transitionTo(ConversationState.Listening)
                    handleApiError(result.exception)
                }
            }
        }
    }

    /**
     * API 오류 처리 (네트워크/서버 오류에 따른 상태 업데이트)
     */
    private fun handleApiError(exception: ApiException) {
        val error = when (exception) {
            is ApiException.NetworkError -> ConversationError.NetworkError
            is ApiException.ServerError -> ConversationError.ServerError
            else -> null
        }

        _uiState.update {
            it.copy(
                currentError = error,
                isRetryButtonEnabled = error != null && it.userRetryCount < MAX_USER_RETRY_COUNT,
                showContactSupport = error != null && it.userRetryCount >= MAX_USER_RETRY_COUNT,
                errorMessage = getErrorMessage(exception)
            )
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
            // Listening/Playing → Sending (이미 Sending이면 중복 요청으로 간주하고 차단)
            if (!transitionTo(ConversationState.Sending)) return@launch

            // 사용자가 종료를 확정했으므로 재생/녹음을 즉시 중지 (재생 중 종료 포함)
            audioPlayerManager.stop()
            audioRecordManager.stop()

            val endedConversationId = conversationId  // nulling되기 전에 캡처 (아래에서 링크 저장에 사용)
            val result = repository.endConversation()

            when (result) {
                is ApiResult.Success -> {
                    stopProcessingTimer()  // PROCESSING 타이머 중지
                    conversationId = null
                    // Sending → Ended
                    transitionTo(ConversationState.Ended)

                    // 일기 생성 결과 확인 (디버깅 단계: 실패를 조용히 삼키지 않음)
                    val diaryStatus = result.data.diaryStatus
                    if (diaryStatus == "FAILED") {
                        Log.w(TAG, "일기 생성 실패 - 사유: ${result.data.diaryError}")
                        Toast.makeText(
                            getApplication(),
                            "일기 생성에 실패했어요: ${result.data.diaryError ?: "알 수 없는 오류"}",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Log.i(TAG, "일기 생성 결과: $diaryStatus (diaryId: ${result.data.diaryId})")
                    }

                    // 서버가 확정한 diaryDate를 이 세션(conversationId)에 매핑해 저장
                    // - 일기 탭에서 로컬 타임스탬프 추정 대신 이 값을 신뢰해 날짜 버킷을 서버와 맞춤
                    val diaryDate = result.data.diaryDate
                    if (diaryDate != null && endedConversationId != null) {
                        launch {
                            runCatching {
                                AppDatabase.getInstance(getApplication()).conversationDiaryLinkDao()
                                    .upsert(ConversationDiaryLinkEntity(endedConversationId, diaryDate))
                            }.onFailure { Log.w(TAG, "대화-일기 날짜 매핑 저장 실패", it) }
                        }
                    }

                    // 일기 로컬 캐시 갱신 (실패해도 무시 - 일기 탭 진입 시 재시도됨)
                    launch {
                        runCatching {
                            DiaryRepository(AppDatabase.getInstance(getApplication()).diaryDao()).refresh()
                        }.onFailure { Log.w(TAG, "일기 캐시 갱신 실패", it) }
                    }

                    // 대화 종료 알림 표시 (10분 후 자동 사라짐)
                    ConversationAlarmReceiver.showFarewellNotification(getApplication())
                    _uiState.update {
                        it.copy(
                            sessionId = null,
                            playbackStatus = PlaybackStatus.NONE,
                            currentError = null,
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
                            showFarewellDialog = false,
                            // 녹음 상태 초기화
                            isSpeechDetected = false,
                            isRecordingPreparing = false
                            // messages는 유지 (대화 기록 보존)
                        )
                    }
                }

                is ApiResult.Error -> {
                    // Sending → Listening (종료 확정 시 오디오를 이미 중지했으므로 발화 대기 재개)
                    transitionTo(ConversationState.Listening)
                    startRecording()
                    _uiState.update { it.copy(errorMessage = getErrorMessage(result.exception)) }
                }
            }
        }
    }

    /**
     * 대화 상태를 업데이트합니다.
     * 내부적으로 전이 검증을 통과한 경우에만 상태가 변경됩니다.
     * - 음성 녹음/재생 상태에 따라 호출
     */
    fun updateConversationState(state: ConversationState) {
        transitionTo(state)
    }

    /**
     * 상태를 IDLE로 초기화합니다.
     * - ENDED 상태에서 사용자가 시작 버튼을 클릭하면 호출
     */
    fun resetToIdle() {
        // Ended → Idle
        transitionTo(ConversationState.Idle)
        _uiState.update { it.copy(messages = emptyList(), sessionId = null, currentError = null) }
    }

    /**
     * 앱이 백그라운드로 전환될 때 호출 (화면 회전 등 구성 변경은 제외)
     * - 마이크 녹음 즉시 중지 (백그라운드에서 마이크가 계속 켜져 있는 것 방지)
     * - TTS 재생 즉시 중지 (백그라운드에서 소리가 계속 나는 것 방지)
     */
    fun onAppBackgrounded() {
        val state = _uiState.value.conversationState
        if (state !is ConversationState.Playing &&
            state !is ConversationState.Listening &&
            state !is ConversationState.Recording
        ) {
            return
        }

        Log.d(TAG, "onAppBackgrounded() - 오디오 중지 (현재 상태: $state)")
        // stop() 과정에서 발생하는 onError가 자동 복구를 트리거하지 않도록 플래그를 먼저 설정
        wasAudioStoppedForBackground = true
        audioPlayerManager.stop()
        stopRecording()

        if (state is ConversationState.Playing) {
            _uiState.update { it.copy(playbackStatus = PlaybackStatus.NONE) }
        }
    }

    /**
     * 앱이 포그라운드로 복귀할 때 호출
     * - 백그라운드 전환 시 오디오를 중지했던 경우에만 발화 대기 재개
     * - 재생 중이던 TTS는 이미 중단되었으므로 Listening으로 전환 후 재개
     */
    fun onAppForegrounded() {
        if (!wasAudioStoppedForBackground) return
        wasAudioStoppedForBackground = false

        when (_uiState.value.conversationState) {
            is ConversationState.Playing, is ConversationState.Recording -> {
                Log.d(TAG, "onAppForegrounded() - Listening으로 전환 후 발화 대기 재개")
                transitionTo(ConversationState.Listening)
                startRecording()
            }
            is ConversationState.Listening -> {
                Log.d(TAG, "onAppForegrounded() - 발화 대기 재개")
                startRecording()
            }
            else -> { /* Idle/Sending/Ended - 재개할 오디오 없음 */ }
        }
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
        _uiState.update { it.copy(errorMessage = null, currentError = null) }
    }

    /**
     * 대화 시작 실패로 인한 홈 복귀 이벤트를 처리 완료로 표시합니다.
     * - ConversationScreen에서 홈으로 복귀하기 직전 호출
     */
    fun consumeStartFailedEvent() {
        _uiState.update { it.copy(startFailed = false) }
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

    /**
     * 현재 상태 → [next] 상태 전이를 검증한 뒤 적용합니다.
     * - 유효한 전이: 상태 업데이트 후 true 반환
     * - 유효하지 않은 전이: Log.w 출력 후 false 반환 (VAD 등 비동기 이벤트 중복 호출도 안전하게 처리)
     *
     * 호출자는 반환값으로 중복 요청을 차단할 수 있습니다:
     * ```
     * if (!transitionTo(ConversationState.Sending)) return@launch
     * ```
     */
    private fun transitionTo(next: ConversationState): Boolean {
        val current = _uiState.value.conversationState
        if (!current.canTransitionTo(next)) {
            Log.w(TAG, "Invalid transition: ${current::class.simpleName} → ${next::class.simpleName}")
            return false
        }
        _uiState.update { it.copy(conversationState = next) }
        return true
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
                    timestamp = message.timestamp,
                    userId = currentUserId
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
        audioRecordManager.release()
        stopProcessingTimer()
    }

    // ═══════════════════════════════════════════════════════════════════
    // 녹음 관련 메서드
    // ═══════════════════════════════════════════════════════════════════

    /**
     * 녹음 시작 (VAD 기반 자동 감지)
     */
    fun startRecording() {
        Log.d(TAG, "startRecording()")
        audioRecordManager.start()
    }

    /**
     * 녹음 중지
     */
    fun stopRecording() {
        Log.d(TAG, "stopRecording()")
        audioRecordManager.stop()
        _uiState.update { it.copy(isSpeechDetected = false) }
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
                currentError = ConversationError.SpeechUnrecognized,
                speechErrorMessage = message,
                speechErrorHint = hint,
                speechFailCount = currentFailCount
            )
        }

        // 2초 후 자동으로 LISTENING으로 복귀
        viewModelScope.launch {
            delay(2000L)
            if (_uiState.value.currentError == ConversationError.SpeechUnrecognized) {
                transitionTo(ConversationState.Listening)
                _uiState.update {
                    it.copy(
                        currentError = null,
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
    // 사용자 재시도 처리
    // ═══════════════════════════════════════════════════════════════════

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
                currentError = null,
                errorMessage = null
            )
        }
        // 재시도 실행
        retryStartConversation()
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
     * 종료 확인 시 대화 종료 처리
     */
    fun onFarewellConfirmed() {
        _uiState.update { it.copy(showFarewellDialog = false) }
        endConversation()
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

    companion object {
        private const val TAG = "ConversationViewModel"

        // 녹음 오류 자동 복구 전 대기 시간 (즉시 재시도 시 같은 오류 반복 방지)
        private const val RECORDER_RECOVERY_DELAY_MS = 1000L

        // AI 음성 재생 완료 후 다음 발화 대기(마이크 On) 전 대기 시간
        // - 재생 종료 즉시 듣기 시작하면 자연스러운 대화 턴 전환 여백이 없어 급하게 느껴짐
        // - 특히 어르신은 "이제 내 차례"를 인지하고 말을 준비하는 데 시간이 더 필요함
        private const val LISTENING_TRANSITION_DELAY_MS = 800L

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY])
                return ConversationViewModel(application) as T
            }
        }
    }
}
