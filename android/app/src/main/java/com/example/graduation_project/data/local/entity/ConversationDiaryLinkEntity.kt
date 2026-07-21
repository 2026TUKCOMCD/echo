package com.example.graduation_project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 로컬 대화 세션(conversationId)이 서버에서 실제로 어느 날짜의 일기로 기록됐는지 매핑
 *
 * 서버는 /end 호출 시점(대화 종료 시각) 기준 KST 날짜로 일기를 기록하므로,
 * 클라이언트가 세션 메시지 타임스탬프로 날짜를 추정(sessionDateKey)하는 대신
 * 이 매핑을 우선 신뢰해 일기 탭의 날짜 버킷을 서버와 정확히 맞춘다.
 * (diaryStatus=SKIPPED이거나 /end 응답을 아직 못 받은 세션은 매핑이 없어
 *  sessionDateKey 휴리스틱으로 폴백한다)
 *
 * @param diaryDate "yyyy-MM-dd" (Asia/Seoul), 서버 ConversationEndResponse.diaryDate 그대로
 */
@Entity(tableName = "conversation_diary_link")
data class ConversationDiaryLinkEntity(
    @PrimaryKey
    val conversationId: String,
    val diaryDate: String
)
