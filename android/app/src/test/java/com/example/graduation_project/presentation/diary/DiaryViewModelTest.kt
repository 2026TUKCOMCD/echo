package com.example.graduation_project.presentation.diary

import android.app.Application
import com.example.graduation_project.data.api.ApiException
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.local.dao.ConversationDiaryLinkDao
import com.example.graduation_project.data.local.dao.MessageDao
import com.example.graduation_project.data.local.dao.SessionRange
import com.example.graduation_project.data.local.entity.ConversationDiaryLinkEntity
import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.data.local.entity.MessageEntity
import com.example.graduation_project.data.repository.DiaryRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 일기 탭 캘린더의 날짜 셀 병합 규칙 검증
 * - 일기가 있는 날 = HasDiary / 세션만 있는 날 = HasSessions / 둘 다 없으면 Empty
 * - 세션 날짜는 서버가 확정한 diaryDate(링크)를 우선, 없으면 세션 종료 시각(KST)으로 폴백
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val kst = ZoneId.of("Asia/Seoul")
    private val userId = 7L
    private val month = YearMonth.now(kst)

    private val messageDao: MessageDao = mockk()
    private val linkDao: ConversationDiaryLinkDao = mockk()
    private val diaryRepository: DiaryRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        coEvery { diaryRepository.refreshMonth(any()) } returns ApiResult.Success(Unit)
        every { diaryRepository.observeMonth(any()) } returns flowOf(emptyList())
        every { messageDao.getSessionRanges(userId) } returns flowOf(emptyList())
        every { linkDao.observeAll() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = DiaryViewModel(
        application = mockk<Application>(relaxed = true),
        messageDao = messageDao,
        conversationDiaryLinkDao = linkDao,
        diaryRepository = diaryRepository,
        currentUserId = userId
    )

    private fun diary(date: LocalDate, status: String = "SUCCESS") = DiaryEntity(
        date = date.toString(), serverId = 1L, title = "제목", content = "내용",
        status = status, failureReason = null, weather = null, mood = null, updatedAt = null
    )

    /** KST 기준 해당 날짜·시각의 epoch ms */
    private fun kstMillis(date: LocalDate, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(date.atTime(hour, minute), kst).toInstant().toEpochMilli()

    /** 세션 하나 + 그 세션의 메시지(사용자/AI 1쌍)를 DAO에 준비 */
    private fun givenSession(id: String, endMillis: Long): SessionRange {
        coEvery { messageDao.getMessagesByConversationIdOnce(id, userId) } returns listOf(
            MessageEntity("$id-u", id, MessageEntity.ROLE_USER, "안녕", endMillis - 60_000, userId),
            MessageEntity("$id-a", id, MessageEntity.ROLE_ASSISTANT, "반가워요", endMillis, userId)
        )
        return SessionRange(id, endMillis - 60_000, endMillis)
    }

    @Test
    fun `일기가 있는 날은 HasDiary이고 그날 세션 수를 함께 담는다`() = runTest(testDispatcher) {
        val day = month.atDay(3)
        every { diaryRepository.observeMonth(month) } returns flowOf(listOf(diary(day)))
        every { messageDao.getSessionRanges(userId) } returns flowOf(
            listOf(givenSession("s1", kstMillis(day, 10)), givenSession("s2", kstMillis(day, 15)))
        )

        val vm = viewModel()
        advanceUntilIdle()

        val cell = vm.uiState.value.cellsByDate[day] as DayCellState.HasDiary
        assertEquals(day.toString(), cell.diary.date)
        assertEquals(2, cell.sessionCount)
    }

    @Test
    fun `일기 없이 세션만 있는 날은 HasSessions, 아무것도 없는 날은 Empty`() = runTest(testDispatcher) {
        val day = month.atDay(5)
        every { messageDao.getSessionRanges(userId) } returns flowOf(listOf(givenSession("s1", kstMillis(day, 9))))

        val vm = viewModel()
        advanceUntilIdle()

        val cells = vm.uiState.value.cellsByDate
        val sessions = cells[day] as DayCellState.HasSessions
        assertEquals(listOf("s1"), sessions.sessions.map { it.conversationId })
        assertEquals(DayCellState.Empty, cells[month.atDay(6)])
        assertEquals(month.lengthOfMonth(), cells.size)
    }

    @Test
    fun `세션 날짜는 서버가 확정한 diaryDate 링크를 종료 시각보다 우선한다`() = runTest(testDispatcher) {
        // 2일 23:50에 끝난 세션이지만 서버는 3일 일기로 기록함
        val endedOn = month.atDay(2)
        val serverDate = month.atDay(3)
        every { messageDao.getSessionRanges(userId) } returns flowOf(listOf(givenSession("s1", kstMillis(endedOn, 23, 50))))
        every { linkDao.observeAll() } returns flowOf(listOf(ConversationDiaryLinkEntity("s1", serverDate.toString())))

        val vm = viewModel()
        advanceUntilIdle()

        val cells = vm.uiState.value.cellsByDate
        assertTrue(cells[serverDate] is DayCellState.HasSessions)
        assertEquals(DayCellState.Empty, cells[endedOn])
    }

    @Test
    fun `링크가 없으면 세션 종료 시각의 KST 날짜로 묶는다`() = runTest(testDispatcher) {
        val day = month.atDay(10)
        every { messageDao.getSessionRanges(userId) } returns flowOf(listOf(givenSession("s1", kstMillis(day, 0, 5))))

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.cellsByDate[day] is DayCellState.HasSessions)
    }

    @Test
    fun `메시지가 없는 세션만 있는 날은 Empty로 둔다`() = runTest(testDispatcher) {
        val day = month.atDay(12)
        coEvery { messageDao.getMessagesByConversationIdOnce("ghost", userId) } returns emptyList()
        every { messageDao.getSessionRanges(userId) } returns flowOf(
            listOf(SessionRange("ghost", kstMillis(day, 8), kstMillis(day, 9)))
        )

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(DayCellState.Empty, vm.uiState.value.cellsByDate[day])
    }

    @Test
    fun `동기화에 실패해도 캐시를 보여주고 오류를 노출하며, 재시도에 성공하면 오류를 지운다`() = runTest(testDispatcher) {
        val day = month.atDay(4)
        every { diaryRepository.observeMonth(month) } returns flowOf(listOf(diary(day)))
        coEvery { diaryRepository.refreshMonth(month) } returns ApiResult.Error(ApiException.NetworkError())

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals("네트워크 연결을 확인해주세요", vm.uiState.value.syncError)
        assertTrue(vm.uiState.value.cellsByDate[day] is DayCellState.HasDiary)

        coEvery { diaryRepository.refreshMonth(month) } returns ApiResult.Success(Unit)
        vm.refresh()
        advanceUntilIdle()

        assertNull(vm.uiState.value.syncError)
    }

    @Test
    fun `다음 달로 이동하면 그 달을 동기화하고 그 달 캐시를 보여준다`() = runTest(testDispatcher) {
        val next = month.plusMonths(1)
        val day = next.atDay(1)
        every { diaryRepository.observeMonth(next) } returns flowOf(listOf(diary(day)))

        val vm = viewModel()
        advanceUntilIdle()
        vm.changeMonth(1)
        advanceUntilIdle()

        coVerify { diaryRepository.refreshMonth(next) }
        assertEquals(next, vm.uiState.value.currentMonth)
        assertTrue(vm.uiState.value.cellsByDate[day] is DayCellState.HasDiary)
        assertEquals(next.lengthOfMonth(), vm.uiState.value.cellsByDate.size)
    }
}
