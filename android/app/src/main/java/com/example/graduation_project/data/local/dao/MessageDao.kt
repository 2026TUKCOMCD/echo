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
 *
 * ## 계정 격리
 * - 모든 조회/삭제 쿼리는 userId로 스코핑됨 (기기 공유·계정 전환 시 다른 계정의
 *   대화 기록이 노출되지 않도록 함). DB v5 이전 레거시 행은 userId=-1로 저장되어
 *   있으며 [backfillLegacyOwner]로 현재 로그인 사용자에게 1회성 귀속된다.
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
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND userId = :userId ORDER BY timestamp ASC")
    fun getMessagesByConversationId(conversationId: String, userId: Long): Flow<List<MessageEntity>>

    /**
     * 특정 대화의 모든 메시지 조회 (일회성)
     * - Flow 없이 단순 조회
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND userId = :userId ORDER BY timestamp ASC")
    suspend fun getMessagesByConversationIdOnce(conversationId: String, userId: Long): List<MessageEntity>

    /**
     * 모든 대화 ID 목록 조회 (최신순)
     * - 대화 목록 화면에서 사용
     */
    @Query("SELECT DISTINCT conversationId FROM messages WHERE userId = :userId ORDER BY timestamp DESC")
    fun getAllConversationIds(userId: Long): Flow<List<String>>

    /**
     * 세션별 시작/종료 시각 조회 (최신순)
     * - 일기 탭에서 날짜별 세션 그룹핑에 사용
     * - 날짜 문자열 변환은 Kotlin에서 Asia/Seoul 기준으로 수행 (SQLite date 함수 미사용)
     */
    @Query("""
        SELECT conversationId, MIN(timestamp) AS firstTimestamp, MAX(timestamp) AS lastTimestamp
        FROM messages WHERE userId = :userId GROUP BY conversationId ORDER BY firstTimestamp DESC
    """)
    fun getSessionRanges(userId: Long): Flow<List<SessionRange>>

    /**
     * 특정 대화의 모든 메시지 삭제
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun deleteMessagesByConversationId(conversationId: String, userId: Long)

    /**
     * 특정 계정의 모든 메시지 삭제
     * - 주의: 해당 계정의 모든 대화 기록이 삭제됨
     */
    @Query("DELETE FROM messages WHERE userId = :userId")
    suspend fun deleteAllMessages(userId: Long)

    /**
     * 특정 대화의 메시지 개수 조회
     */
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND userId = :userId")
    suspend fun getMessageCount(conversationId: String, userId: Long): Int

    /**
     * 특정 대화의 마지막 메시지 조회
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND userId = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: String, userId: Long): MessageEntity?

    /**
     * DB v5 마이그레이션 직후 남아있는 레거시 행(userId=-1)을 현재 로그인 사용자에게 귀속.
     * 이미 귀속된 행은 없으므로 앱 시작 시마다 호출해도 안전(멱등)함.
     * @return 갱신된 행 수
     */
    @Query("UPDATE messages SET userId = :userId WHERE userId = -1")
    suspend fun backfillLegacyOwner(userId: Long): Int
}

/**
 * 세션(대화)별 시작/종료 시각 집계 결과
 */
data class SessionRange(
    val conversationId: String,
    val firstTimestamp: Long,
    val lastTimestamp: Long
)
