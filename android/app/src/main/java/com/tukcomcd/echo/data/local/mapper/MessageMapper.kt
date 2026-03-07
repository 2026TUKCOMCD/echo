package com.tukcomcd.echo.data.local.mapper

import com.tukcomcd.echo.data.local.entity.MessageEntity
import com.tukcomcd.echo.presentation.model.MessageUiModel

/**
 * MessageEntity <-> MessageUiModel 변환 함수
 *
 * ## 사용 예시
 * ```kotlin
 * // Entity -> UiModel
 * val uiModel = entity.toUiModel()
 *
 * // UiModel -> Entity
 * val entity = uiModel.toEntity(conversationId = "session-123")
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
 */
fun MessageUiModel.toEntity(conversationId: String): MessageEntity {
    return MessageEntity(
        id = this.id,
        conversationId = conversationId,
        role = if (this.isFromUser) MessageEntity.ROLE_USER else MessageEntity.ROLE_ASSISTANT,
        content = this.text,
        timestamp = this.timestamp
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
fun List<MessageUiModel>.toEntities(conversationId: String): List<MessageEntity> {
    return this.map { it.toEntity(conversationId) }
}
