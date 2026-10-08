package com.example.graduation_project.presentation.diary

import android.app.Application
import com.example.graduation_project.data.local.dao.ConversationDiaryLinkDao
import com.example.graduation_project.data.local.dao.DiaryDao
import com.example.graduation_project.data.local.dao.MessageDao
import com.example.graduation_project.data.local.dao.SessionRange
import com.example.graduation_project.data.local.entity.ConversationDiaryLinkEntity
import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.data.local.entity.MessageEntity
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 일기 상세 화면: 그날의 일기 + 같은 날짜 버킷의 세션만 보여주는지 검증
 * (버킷 기준은 캘린더와 동일 - diaryDate 링크 우선, 없으면 종료 시각 KST)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiaryDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val kst = ZoneId.of("Asia/Seoul")
    private val userId = 7L
    private val date = LocalDate.of(2026, 9, 15)

    private val messageDao: MessageDao = mockk()
    private val diaryDao: DiaryDao = mockk()
    private val linkDao: ConversationDiaryLinkDao = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { linkDao.observeAll() } returns flowOf(emptyList())
        coEvery { diaryDao.getByDate(any()) } returns null
        every { diaryDao.observeByDate(any()) } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DiaryDetailViewModel(
        application = mockk<Application>(relaxed = true),
        date = date.toString(),
        messageDao = messageDao,
        diaryDao = diaryDao,
        conversationDiaryLinkDao = linkDao,
        currentUserId = userId
    )

    private fun diary(status: String, content: String?) = DiaryEntity(
        date = date.toString(), serverId = 1L, title = "제목", content = content,
        status = status, failureReason = null, weather = null, mood = null, updatedAt = null
    )

    private fun kstMillis(day: LocalDate, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(day.atTime(hour, minute), kst).toInstant().toEpochMilli()

    private fun givenSession(id: String, endMillis: Long): SessionRange {
        coEvery { messageDao.getMessagesByConversationIdOnce(id, userId) } returns listOf(
            MessageEntity("$id-u", id, MessageEntity.ROLE_USER, "안녕", endMillis - 60_000, userId),
            MessageEntity("$id-a", id, MessageEntity.ROLE_ASSISTANT, "반가워요", endMillis, userId)
        )
        return SessionRange(id, endMillis - 60_000, endMillis)
    }

    @Test
    fun `그날 일기와 같은 날짜 버킷의 세션만 보여준다`() = runTest(testDispatcher) {
        val diary = DiaryEntity(
            date = date.toString(), serverId = 1L, title = "제목", content = "내용",
            status = "SUCCESS", failureReason = null, weather = null, mood = null, updatedAt = null
        )
        coEvery { diaryDao.getByDate(date.toString()) } returns diary
        every { diaryDao.observeByDate(date.toString()) } returns flowOf(diary)
        every { messageDao.getSessionRanges(userId) } returns flowOf(
            listOf(
                givenSession("today", kstMillis(date, 10)),
                givenSession("yesterday", kstMillis(date.minusDays(1), 10)),
                // 전날 밤 끝났지만 서버가 이 날짜 일기로 기록한 세션
                givenSession("linked", kstMillis(date.minusDays(1), 23, 50))
            )
        )
        every { linkDao.observeAll() } returns flowOf(listOf(ConversationDiaryLinkEntity("linked", date.toString())))

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertEquals(diary, state.diary)
        assertEquals(setOf("today", "linked"), state.sessions.map { it.conversationId }.toSet())
    }

    @Test
    fun `일기가 없는 날은 diary 없이 세션만 보여준다`() = runTest(testDispatcher) {
        every { messageDao.getSessionRanges(userId) } returns flowOf(listOf(givenSession("s1", kstMillis(date, 14))))

        val vm = viewModel()
        advanceUntilIdle()

        assertNull(vm.uiState.value.diary)
        assertEquals(listOf("s1"), vm.uiState.value.sessions.map { it.conversationId })
    }

    @Test
    fun `상세 화면이 열려 있는 동안 일기 캐시가 갱신되면 화면도 바뀐다`() = runTest(testDispatcher) {
        // 캘린더의 달 동기화가 끝나기 전에 상세로 들어온 상황: 처음엔 이전 캐시(갱신 실패)
        val stale = diary("FAILED", "이전 일기")
        val fresh = diary("SUCCESS", "새로 쓴 일기")
        val cached = MutableStateFlow<DiaryEntity?>(stale)
        coEvery { diaryDao.getByDate(date.toString()) } answers { cached.value }
        every { diaryDao.observeByDate(date.toString()) } returns cached
        every { messageDao.getSessionRanges(userId) } returns flowOf(emptyList())

        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(stale, vm.uiState.value.diary)

        cached.value = fresh   // 동기화 결과가 캐시에 저장됨 (세션·링크는 그대로)
        advanceUntilIdle()

        assertEquals(fresh, vm.uiState.value.diary)
    }
}
