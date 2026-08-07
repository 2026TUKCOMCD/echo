package com.example.graduation_project.presentation.diary

import com.example.graduation_project.data.local.dao.MessageDao
import com.example.graduation_project.data.local.dao.SessionRange
import com.example.graduation_project.data.local.entity.MessageEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * resolveDateKey() / buildSessionSummary()의 날짜 계산 회귀 테스트
 *
 * 서버는 /end 호출 시점(대화 종료 시각) 기준 KST 날짜로 일기를 기록하므로,
 * - 서버가 확정한 diaryDate(ConversationDiaryLinkEntity)가 있으면 그 값을 그대로 신뢰하고
 * - 없으면(SKIPPED, 오프라인 등) lastTimestamp(마지막 메시지 시각) 휴리스틱으로 폴백해야
 * 자정을 넘긴 대화에서 서버 일기 날짜와 어긋나지 않는다.
 */
class SessionSummariesTest {

    private val mockMessageDao = mockk<MessageDao>()

    // 첫 메시지 1/15 23:50 KST, 마지막 메시지 1/16 00:10 KST (자정 경계)
    private val firstTimestamp = Instant.parse("2026-01-15T14:50:00Z").toEpochMilli()
    private val lastTimestamp = Instant.parse("2026-01-15T15:10:00Z").toEpochMilli()
    private val range = SessionRange(
        conversationId = "conv-1",
        firstTimestamp = firstTimestamp,
        lastTimestamp = lastTimestamp
    )

    @Test
    fun `resolveDateKey는 서버 diaryDate가 있으면 그 값을 그대로 사용한다`() {
        // given: 서버가 이 세션을 1/17로 확정한 상황 (lastTimestamp 추정과는 다른 날짜)
        val serverDiaryDate = "2026-01-17"

        // when
        val result = resolveDateKey(range, serverDiaryDate)

        // then
        assertEquals(serverDiaryDate, result)
    }

    @Test
    fun `resolveDateKey는 diaryDate 매핑이 없으면 lastTimestamp 기준으로 폴백한다`() {
        // when: 매핑 없음(null) - SKIPPED이거나 아직 /end 응답을 못 받은 세션
        val result = resolveDateKey(range, linkedDiaryDate = null)

        // then: 자정을 넘긴 대화이므로 1/15가 아니라 1/16(lastTimestamp 날짜)이어야 함
        assertEquals("2026-01-16", result)
    }

    @Test
    fun `buildSessionSummary는 전달받은 dateKey를 그대로 표시 날짜로 사용한다`() = runTest {
        // given
        val messages = listOf(
            MessageEntity("m1", "conv-1", MessageEntity.ROLE_USER, "안녕하세요", firstTimestamp, userId = 1L),
            MessageEntity("m2", "conv-1", MessageEntity.ROLE_ASSISTANT, "안녕하세요, 오늘 하루 어떠셨나요?", lastTimestamp, userId = 1L)
        )
        coEvery { mockMessageDao.getMessagesByConversationIdOnce("conv-1", 1L) } returns messages

        // when: 호출부(DiaryViewModel)가 그룹핑에 쓴 것과 동일한 dateKey를 전달
        val summary = buildSessionSummary(mockMessageDao, range, dateKey = "2026-01-17", userId = 1L)

        // then: buildSessionSummary가 내부적으로 다시 계산하지 않고 전달받은 dateKey를 그대로 반영해야
        // 목록 그룹핑과 카드에 표시되는 날짜가 항상 일치함
        assertEquals(formatKoreanDate("2026-01-17"), summary?.date)
    }
}
