package com.example.graduation_project.data.local.mapper

import com.example.graduation_project.data.local.entity.MessageEntity
import com.example.graduation_project.presentation.model.MessageUiModel

/**
 * MessageEntity <-> MessageUiModel 변환 함수
 *
 * ## 사용 예시
 * ```kotlin
 * // Entity -> UiModel
 * val uiModel = entity.toUiModel()
 *
 * // UiModel -> Entity
 * val entity = uiModel.toEntity(conversationId = "session-123", userId = 1L)
 * ```
 */

/**
 * MessageEntity를 MessageUiModel로 변환
 */
fun MessageEntity.toUiModel(): MessageUiModel {
    return MessageUiModel(
        id = this.id,
        text = this.content,
        isFromUser = this.role == MessageEntity.ROLE_USER,
        timestamp = this.timestamp
    )
}

/**
 * MessageUiModel을 MessageEntity로 변환
 * @param conversationId 대화 세션 ID (Entity에 필요)
 * @param userId 메시지 소유자 (계정별 로컬 캐시 격리용)
 */
fun MessageUiModel.toEntity(conversationId: String, userId: Long): MessageEntity {
    return MessageEntity(
        id = this.id,
        conversationId = conversationId,
        role = if (this.isFromUser) MessageEntity.ROLE_USER else MessageEntity.ROLE_ASSISTANT,
        content = this.text,
        timestamp = this.timestamp,
        userId = userId
    )
}

/**
 * MessageEntity 리스트를 MessageUiModel 리스트로 변환
 */
fun List<MessageEntity>.toUiModels(): List<MessageUiModel> {
    return this.map { it.toUiModel() }
}

/**
 * MessageUiModel 리스트를 MessageEntity 리스트로 변환
 */
fun List<MessageUiModel>.toEntities(conversationId: String, userId: Long): List<MessageEntity> {
    return this.map { it.toEntity(conversationId, userId) }
}
