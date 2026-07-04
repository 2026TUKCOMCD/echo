package com.example.graduation_project.presentation.conversation

import android.app.Application
import android.util.Log
import com.example.graduation_project.data.alarm.ConversationAlarmReceiver
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.dao.MessageDao
import com.example.graduation_project.data.location.LocationCollectionService
import com.example.graduation_project.data.location.LocationDataManager
import com.example.graduation_project.data.model.ConversationEndResponse
import com.example.graduation_project.data.model.ConversationMessageResponse
import com.example.graduation_project.data.model.ConversationStartResponse
import com.example.graduation_project.data.repository.ConversationRepository
import com.example.graduation_project.data.voice.AudioPlayerManager
import com.example.graduation_project.data.voice.AudioRecordManager
import com.example.graduation_project.domain.health.HealthConnectAvailability
import com.example.graduation_project.domain.health.IHealthRepository
import com.example.graduation_project.domain.voice.AudioRecordException
import com.example.graduation_project.domain.voice.AudioRecordListener
import com.example.graduation_project.domain.voice.AudioRecordState
import com.example.graduation_project.presentation.model.ConversationState
import io.mockk.CapturingSlot
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * ConversationViewModel 단위 테스트
 *
 * ## 테스트 대상
 * - 중복 요청 방지: 동일 액션 연속 호출 시 API가 1번만 실행되는지 검증
 * - 에러 복구: 실패 시 올바른 상태로 복구되는지 검증
 *
 * ## 테스트 환경
 * - viewModelScope(Dispatchers.Main)를 StandardTestDispatcher로 교체
 * - ConversationRepository를 mockk으로 대체
 * - AppDatabase/MessageDao를 mockk으로 대체 (생성자 주입)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mockRepository = mockk<ConversationRepository>()
    private val mockMessageDao = mockk<MessageDao>(relaxed = true)
    private val mockApplication = mockk<Application>(relaxed = true)
    private val mockAudioRecordState = MutableStateFlow<AudioRecordState>(AudioRecordState.Idle)
    private val mockAudioRecordManager = mockk<AudioRecordManager>(relaxed = true)
    private val mockAudioPlayerManager = mockk<AudioPlayerManager>(relaxed = true)
    private val mockHealthRepository = mockk<IHealthRepository>(relaxed = true)
    private val mockLocationDataManager = mockk<LocationDataManager>(relaxed = true)

    private lateinit var viewModel: ConversationViewModel
    private val audioRecordListenerSlot: CapturingSlot<AudioRecordListener> = slot()

    @Before
    fun setUp() {
        // android.util.Log은 JVM 단위 테스트에서 사용 불가 → static mock으로 대체
        mockkStatic(Log::class)
        every { Log.d(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>()) } returns 0
        every { Log.e(any(), any<String>(), any()) } returns 0

        // LocationCollectionService companion object mock
        mockkObject(LocationCollectionService)
        every { LocationCollectionService.stop(any()) } just Runs
        every { LocationCollectionService.start(any()) } just Runs

        // ConversationAlarmReceiver companion object mock
        mockkObject(ConversationAlarmReceiver)
        every { ConversationAlarmReceiver.cancelNotification(any()) } just Runs
        every { ConversationAlarmReceiver.showFarewellNotification(any()) } just Runs

        every { mockAudioRecordManager.state } returns mockAudioRecordState
        every { mockAudioRecordManager.setListener(capture(audioRecordListenerSlot)) } just Runs
        every { mockHealthRepository.getAvailability() } returns HealthConnectAvailability.NotSupported

        viewModel = ConversationViewModel(
            application = mockApplication,
            repository = mockRepository,
            messageDao = mockMessageDao,
            audioRecordManager = mockAudioRecordManager,
            healthRepository = mockHealthRepository,
            locationDataManager = mockLocationDataManager,
            audioPlayerManager = mockAudioPlayerManager
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        unmockkObject(LocationCollectionService)
        unmockkObject(ConversationAlarmReceiver)
    }

    // ===== 중복 요청 방지 테스트 =====

    @Test
    fun `startConversation 중복 호출 시 repository는 1번만 호출된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // 진행 중(Sending) 상태가 유지되도록 delay로 첫 번째 호출을 지연
            coEvery { mockRepository.startConversation(any(), any()) } coAnswers {
                delay(1_000)
                ApiResult.Success(ConversationStartResponse(message = "안녕하세요"))
            }

            viewModel.startConversation()  // Idle → Sending, delay(1000)에서 대기
            viewModel.startConversation()  // Sending → Sending 전이 실패 → return@launch
            advanceUntilIdle()

            coVerify(exactly = 1) { mockRepository.startConversation(any(), any()) }
        }

    @Test
    fun `sendMessage 중복 호출 시 repository는 1번만 호출된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setupListeningState()

            coEvery { mockRepository.sendMessage(any()) } coAnswers {
                delay(1_000)
                ApiResult.Success(ConversationMessageResponse())
            }

            val wavData = ByteArray(0)
            viewModel.sendMessage(wavData)  // Listening → Sending, 대기
            viewModel.sendMessage(wavData)  // Sending → Sending 전이 실패 → return@launch
            advanceUntilIdle()

            coVerify(exactly = 1) { mockRepository.sendMessage(any()) }
        }

    @Test
    fun `endConversation 중복 호출 시 repository는 1번만 호출된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setupListeningState()

            coEvery { mockRepository.endConversation() } coAnswers {
                delay(1_000)
                ApiResult.Success(ConversationEndResponse())
            }

            viewModel.endConversation()  // Listening → Sending, 대기
            viewModel.endConversation()  // Sending → Sending 전이 실패 → return@launch
            advanceUntilIdle()

            coVerify(exactly = 1) { mockRepository.endConversation() }
        }

    // ===== 에러 복구 테스트 =====

    @Test
    fun `startConversation 실패 시 Idle로 복구된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { mockRepository.startConversation(any(), any()) } returns
                ApiResult.Error(ApiException.NetworkError())

            viewModel.startConversation()
            advanceUntilIdle()

            assertEquals(ConversationState.Idle, viewModel.uiState.value.conversationState)
        }

    @Test
    fun `sendMessage 실패 시 Listening으로 복구된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setupListeningState()
            coEvery { mockRepository.sendMessage(any()) } returns
                ApiResult.Error(ApiException.NetworkError())

            viewModel.sendMessage(ByteArray(0))
            advanceUntilIdle()

            assertEquals(ConversationState.Listening, viewModel.uiState.value.conversationState)
        }

    @Test
    fun `endConversation 실패 시 Listening으로 복구된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setupListeningState()
            coEvery { mockRepository.endConversation() } returns
                ApiResult.Error(ApiException.NetworkError())

            viewModel.endConversation()
            advanceUntilIdle()

            assertEquals(ConversationState.Listening, viewModel.uiState.value.conversationState)
        }

    // ===== endConversation 성공 테스트 =====

    @Test
    fun `endConversation 성공 시 Ended 상태로 전환된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setupListeningState()

            coEvery { mockRepository.endConversation() } returns
                ApiResult.Success(ConversationEndResponse())

            viewModel.endConversation()
            advanceUntilIdle()

            assertEquals(ConversationState.Ended, viewModel.uiState.value.conversationState)
        }

    // ===== 재생 중 대화 종료 테스트 =====

    @Test
    fun `Playing 상태에서 endConversation 호출 시 Ended로 전환된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // audioData가 있어야 Playing 상태가 유지됨 (없으면 즉시 Listening으로 전환)
            coEvery { mockRepository.startConversation(any(), any()) } returns
                ApiResult.Success(ConversationStartResponse(message = "안녕하세요", audioData = "dummy-audio"))
            viewModel.startConversation()
            advanceUntilIdle()
            // 현재 상태: Playing (AI가 말하는 중)
            assertEquals(ConversationState.Playing, viewModel.uiState.value.conversationState)

            coEvery { mockRepository.endConversation() } returns
                ApiResult.Success(ConversationEndResponse())

            viewModel.endConversation()
            advanceUntilIdle()

            // 재생 중에도 대화 종료가 동작해야 함 (기존에는 조용히 무시되던 버그)
            assertEquals(ConversationState.Ended, viewModel.uiState.value.conversationState)
        }

    // ===== 녹음 오류 자동 복구 테스트 =====

    @Test
    fun `녹음 오류 발생 시 Listening 상태면 자동으로 재시도한다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setupListeningState()

            // 녹음 오류 발생 (권한 회수, 마이크 점유 등)
            audioRecordListenerSlot.captured.onError(AudioRecordException.UnknownError())
            advanceUntilIdle()

            // 마이크가 죽은 채 방치되지 않고 자동 복구를 시도해야 함
            verify { mockAudioRecordManager.resumeListening() }
            assertEquals(ConversationState.Listening, viewModel.uiState.value.conversationState)
        }

    @Test
    fun `녹음 오류가 연속으로 발생하면 복구 시도를 중단하고 안내 메시지를 표시한다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setupListeningState()

            // 최대 복구 횟수(3회) + 1회 오류 발생
            repeat(4) {
                audioRecordListenerSlot.captured.onError(AudioRecordException.UnknownError())
                advanceUntilIdle()
            }

            // 복구는 3회까지만 시도되고 이후에는 사용자 안내
            verify(exactly = 3) { mockAudioRecordManager.resumeListening() }
            assertEquals(
                "마이크를 사용할 수 없어요. 마이크 권한을 확인해주세요.",
                viewModel.uiState.value.errorMessage
            )
        }

    // ===== 백그라운드 전환 테스트 =====

    @Test
    fun `백그라운드 전환 후 복귀하면 Listening으로 재개된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { mockRepository.startConversation(any(), any()) } returns
                ApiResult.Success(ConversationStartResponse(message = "안녕하세요", audioData = "dummy-audio"))
            viewModel.startConversation()
            advanceUntilIdle()
            // 현재 상태: Playing

            viewModel.onAppBackgrounded()
            viewModel.onAppForegrounded()
            advanceUntilIdle()

            // 재생은 중단되었으므로 발화 대기(Listening)로 재개
            assertEquals(ConversationState.Listening, viewModel.uiState.value.conversationState)
            verify { mockAudioRecordManager.start() }
        }

    @Test
    fun `백그라운드 전환으로 녹음 중지 시 발생하는 오류는 자동 복구를 트리거하지 않는다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            setupListeningState()

            // 백그라운드 전환 → stopRecording() 과정에서 VAD 취소가 onError로 전파되는 상황 재현
            viewModel.onAppBackgrounded()
            audioRecordListenerSlot.captured.onError(AudioRecordException.UnknownError())
            advanceUntilIdle()

            // 의도적 중지이므로 백그라운드에서 마이크를 다시 켜면 안 됨
            verify(exactly = 0) { mockAudioRecordManager.resumeListening() }
        }

    @Test
    fun `백그라운드 전환 없이 복귀 이벤트만 오면 아무것도 하지 않는다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            coEvery { mockRepository.startConversation(any(), any()) } returns
                ApiResult.Success(ConversationStartResponse(message = "안녕하세요", audioData = "dummy-audio"))
            viewModel.startConversation()
            advanceUntilIdle()
            // 현재 상태: Playing (화면 회전 등으로 ON_START만 오는 경우 재현)

            viewModel.onAppForegrounded()
            advanceUntilIdle()

            // 백그라운드로 중지한 적이 없으므로 상태 유지 (재생 중 회전해도 대화 유지)
            assertEquals(ConversationState.Playing, viewModel.uiState.value.conversationState)
        }

    // ===== 헬퍼 =====

    /**
     * sendMessage/endConversation 테스트를 위해 Listening 상태로 설정
     * 경로: Idle → Sending → Playing → Listening
     *
     * advanceUntilIdle()은 TestScope의 확장 함수이므로 TestScope 수신자로 선언
     */
    private fun TestScope.setupListeningState() {
        coEvery { mockRepository.startConversation(any(), any()) } returns
            ApiResult.Success(ConversationStartResponse(message = "안녕하세요"))

        viewModel.startConversation()
        advanceUntilIdle()
        // 현재 상태: Playing (startConversation 성공 후)
        // Playing → Listening 전이
        viewModel.updateConversationState(ConversationState.Listening)
    }
}

/**
 * viewModelScope가 사용하는 Dispatchers.Main을 테스트용 디스패처로 교체
 * runTest에 동일한 testDispatcher를 전달해 가상 시간을 공유
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description?) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description?) = Dispatchers.resetMain()
}
