package com.example.graduation_project.presentation.conversation

import android.app.Application
import android.util.Log
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.dao.MessageDao
import com.example.graduation_project.data.model.ConversationEndResponse
import com.example.graduation_project.data.model.ConversationMessageResponse
import com.example.graduation_project.data.model.ConversationStartResponse
import com.example.graduation_project.data.repository.ConversationRepository
import com.example.graduation_project.presentation.model.ConversationState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

    private lateinit var viewModel: ConversationViewModel

    @Before
    fun setUp() {
        // android.util.Log은 JVM 단위 테스트에서 사용 불가 → static mock으로 대체
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0

        viewModel = ConversationViewModel(
            application = mockApplication,
            repository = mockRepository,
            messageDao = mockMessageDao
        )
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    // ===== 중복 요청 방지 테스트 =====

    @Test
    fun `startConversation 중복 호출 시 repository는 1번만 호출된다`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // 진행 중(Sending) 상태가 유지되도록 delay로 첫 번째 호출을 지연
            coEvery { mockRepository.startConversation(any()) } coAnswers {
                delay(1_000)
                ApiResult.Success(ConversationStartResponse(message = "안녕하세요"))
            }

            viewModel.startConversation()  // Idle → Sending, delay(1000)에서 대기
            viewModel.startConversation()  // Sending → Sending 전이 실패 → return@launch
            advanceUntilIdle()

            coVerify(exactly = 1) { mockRepository.startConversation(any()) }
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
            coEvery { mockRepository.startConversation(any()) } returns
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

    // ===== 헬퍼 =====

    /**
     * sendMessage/endConversation 테스트를 위해 Listening 상태로 설정
     * 경로: Idle → Sending → Playing → Listening
     *
     * advanceUntilIdle()은 TestScope의 확장 함수이므로 TestScope 수신자로 선언
     */
    private fun TestScope.setupListeningState() {
        coEvery { mockRepository.startConversation(any()) } returns
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
