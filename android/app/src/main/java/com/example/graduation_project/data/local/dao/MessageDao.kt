package com.example.graduation_project.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.graduation_project.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 메시지 데이터 접근 객체 (DAO)
 *
 * ## 주요 기능
 * - 메시지 저장 (단일/다중)
 * - 대화별 메시지 조회 (Flow로 실시간 업데이트)
 * - 메시지 삭제 (대화별/전체)
 *
 * ## Flow 사용
 * - Flow를 반환하면 데이터 변경 시 자동으로 UI 업데이트
 * - Compose의 collectAsState()와 함께 사용
 */
@Dao
interface MessageDao {

    /**
     * 단일 메시지 저장
     * - 같은 ID가 있으면 덮어쓰기
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    /**
     * 여러 메시지 한번에 저장
     * - 대화 복원 시 사용
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * 특정 대화의 모든 메시지 조회 (시간순 정렬)
     * - Flow로 반환하여 실시간 업데이트 지원
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesByConversationId(conversationId: String): Flow<List<MessageEntity>>

    /**
     * 특정 대화의 모든 메시지 조회 (일회성)
     * - Flow 없이 단순 조회
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesByConversationIdOnce(conversationId: String): List<MessageEntity>

    /**
     * 모든 대화 ID 목록 조회 (최신순)
     * - 대화 목록 화면에서 사용
     */
    @Query("SELECT DISTINCT conversationId FROM messages ORDER BY timestamp DESC")
    fun getAllConversationIds(): Flow<List<String>>

    /**
     * 특정 대화의 모든 메시지 삭제
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversationId(conversationId: String)

    /**
     * 모든 메시지 삭제
     * - 주의: 모든 대화 기록이 삭제됨
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()

    /**
     * 특정 대화의 메시지 개수 조회
     */
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    /**
     * 특정 대화의 마지막 메시지 조회
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: String): MessageEntity?
}
