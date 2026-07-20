package com.example.graduation_project.presentation.diary

import com.example.graduation_project.data.local.dao.MessageDao
import com.example.graduation_project.data.local.dao.SessionRange
import com.example.graduation_project.data.local.entity.MessageEntity
import com.example.graduation_project.presentation.model.ConversationSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * 일기 탭에서 로컬 대화 세션을 날짜별로 묶기 위한 공용 헬퍼
 *
 * 서버 일기의 날짜(Asia/Seoul 기준 "yyyy-MM-dd")와 매칭되도록
 * 세션 날짜 계산도 반드시 Asia/Seoul 기준으로 수행
 */
internal val KST: TimeZone = TimeZone.getTimeZone("Asia/Seoul")

/** timestamp(ms) → "yyyy-MM-dd" (Asia/Seoul) */
internal fun sessionDateKey(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).apply { timeZone = KST }.format(Date(timestamp))

/** "yyyy-MM-dd" → "yyyy년 M월 d일 (E)" */
internal fun formatKoreanDate(dateKey: String): String {
    val parser = SimpleDateFormat("yyyy-MM-dd", Locale.KOREAN).apply { timeZone = KST }
    val date = parser.parse(dateKey) ?: return dateKey
    return SimpleDateFormat("yyyy년 M월 d일 (E)", Locale.KOREAN).apply { timeZone = KST }.format(date)
}

/** timestamp(ms) → "오전 h:mm" (Asia/Seoul) */
internal fun formatKoreanTime(timestamp: Long): String =
    SimpleDateFormat("a h:mm", Locale.KOREAN).apply { timeZone = KST }.format(Date(timestamp))

/**
 * 세션의 메시지를 조회해 목록 카드용 요약 생성
 * (기존 대화기록 탭의 buildSummary 로직 이식)
 */
internal suspend fun buildSessionSummary(messageDao: MessageDao, range: SessionRange): ConversationSummary? {
    val messages = messageDao.getMessagesByConversationIdOnce(range.conversationId)
    if (messages.isEmpty()) return null

    val first = messages.first()
    val last = messages.last()
    val durationMin = maxOf(1, TimeUnit.MILLISECONDS.toMinutes(last.timestamp - first.timestamp).toInt())

    val preview = messages.firstOrNull { it.role == MessageEntity.ROLE_ASSISTANT }
        ?.content?.take(60) ?: messages.first().content.take(60)

    return ConversationSummary(
        conversationId = range.conversationId,
        date = formatKoreanDate(sessionDateKey(first.timestamp)),
        timeRange = "${formatKoreanTime(first.timestamp)} ~ ${formatKoreanTime(last.timestamp)}",
        durationMin = durationMin,
        previewText = preview
    )
}
