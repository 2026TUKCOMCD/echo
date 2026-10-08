package com.example.graduation_project.presentation.diary

import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.presentation.model.ConversationSummary
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * 캘린더 날짜 칸 상태 판정과 TalkBack 문구 검증
 * - 배경색이 이 상태로 결정되므로, 상태가 맞으면 화면 표시도 맞음
 */
class DayCellStatusTest {

    private fun diary(status: String, content: String?) = DiaryEntity(
        date = "2026-10-05",
        serverId = 1L,
        title = null,
        content = content,
        status = status,
        failureReason = null,
        weather = null,
        mood = null,
        updatedAt = null
    )

    @Test
    fun `성공한 일기는 SUCCESS`() {
        val state = DayCellState.HasDiary(diary("SUCCESS", "오늘은 산책을 했다"), sessionCount = 1)
        assertEquals(DayCellStatus.SUCCESS, state.toStatus())
    }

    @Test
    fun `실패했지만 이전 내용이 남은 일기는 STALE`() {
        val state = DayCellState.HasDiary(diary("FAILED", "이전 일기"), sessionCount = 2)
        assertEquals(DayCellStatus.STALE, state.toStatus())
    }

    @Test
    fun `실패하고 내용도 없는 일기는 FAILURE`() {
        val state = DayCellState.HasDiary(diary("FAILED", null), sessionCount = 1)
        assertEquals(DayCellStatus.FAILURE, state.toStatus())
    }

    @Test
    fun `일기 없이 대화만 있으면 SESSION_ONLY, 아무것도 없으면 EMPTY`() {
        val sessions = DayCellState.HasSessions(listOf(mockk<ConversationSummary>()))
        assertEquals(DayCellStatus.SESSION_ONLY, sessions.toStatus())
        assertEquals(DayCellStatus.EMPTY, DayCellState.Empty.toStatus())
    }

    @Test
    fun `TalkBack 문구는 날짜·요일·상태를 읽고 오늘이면 앞에 알림`() {
        val date = LocalDate.of(2026, 10, 5) // 월요일
        assertEquals(
            "10월 5일 월요일, 일기 생성 실패",
            dayCellDescription(date, DayCellStatus.FAILURE, isToday = false)
        )
        assertEquals(
            "오늘, 10월 5일 월요일, 대화만 있음",
            dayCellDescription(date, DayCellStatus.SESSION_ONLY, isToday = true)
        )
    }
}
