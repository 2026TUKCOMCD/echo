package com.example.graduation_project.presentation.diary

import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * 캘린더 날짜 칸의 표시 상태
 *
 * 배경색·TalkBack 문구가 모두 이 상태 하나로 결정되도록 모아 둠
 */
enum class DayCellStatus(val label: String) {
    SUCCESS("일기 있음"),
    STALE("일기 갱신 실패"),        // FAILED지만 이전 일기 내용이 남아 있음
    FAILURE("일기 생성 실패"),      // FAILED이고 보여줄 내용이 없음
    SESSION_ONLY("대화만 있음"),
    EMPTY("기록 없음")
}

fun DayCellState.toStatus(): DayCellStatus = when (this) {
    is DayCellState.HasDiary -> when {
        diary.status != "FAILED" -> DayCellStatus.SUCCESS
        diary.content != null -> DayCellStatus.STALE
        else -> DayCellStatus.FAILURE
    }
    is DayCellState.HasSessions -> DayCellStatus.SESSION_ONLY
    DayCellState.Empty -> DayCellStatus.EMPTY
}

/**
 * TalkBack이 날짜 칸을 읽을 문구 (예: "오늘, 10월 5일 월요일, 일기 있음")
 */
fun dayCellDescription(date: LocalDate, status: DayCellStatus, isToday: Boolean): String {
    val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
    val prefix = if (isToday) "오늘, " else ""
    return "$prefix${date.monthValue}월 ${date.dayOfMonth}일 $dayOfWeek, ${status.label}"
}
