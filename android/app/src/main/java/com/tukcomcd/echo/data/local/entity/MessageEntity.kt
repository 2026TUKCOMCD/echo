package com.tukcomcd.echo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 대화 메시지를 저장하는 Room Entity
 *
 * ## 테이블 구조
 * - 테이블명: messages
 * - 대화 세션별로 메시지를 그룹화 (conversationId)
 *
 * @param id 메시지 고유 ID (UUID 문자열)
 * @param conversationId 대화 세션 ID (같은 대화의 메시지들은 동일한 값)
 * @param role 발화자 역할 ("user" 또는 "assistant")
 * @param content 메시지 내용
 * @param timestamp 메시지 생성 시간 (밀리초)
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val timestamp: Long
) {
    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}
