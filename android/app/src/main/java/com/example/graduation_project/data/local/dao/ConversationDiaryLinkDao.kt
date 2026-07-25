package com.example.graduation_project.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.graduation_project.data.local.entity.ConversationDiaryLinkEntity
import kotlinx.coroutines.flow.Flow

/**
 * conversationId → 서버가 확정한 diaryDate 매핑 DAO
 * - 일기 탭에서 로컬 세션의 날짜 버킷을 서버와 정확히 맞추는 데 사용
 */
@Dao
interface ConversationDiaryLinkDao {

    /**
     * /end 응답으로 diaryDate를 받았을 때 매핑 저장
     * - 같은 conversationId면 덮어쓰기 (재종료·재시도 대비)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(link: ConversationDiaryLinkEntity)

    /**
     * 전체 매핑 조회 (Flow) - 일기 탭 목록 병합에 사용
     */
    @Query("SELECT * FROM conversation_diary_link")
    fun observeAll(): Flow<List<ConversationDiaryLinkEntity>>

    /**
     * 단건 조회 - 일기 상세 화면에서 사용
     */
    @Query("SELECT diaryDate FROM conversation_diary_link WHERE conversationId = :conversationId")
    suspend fun getDiaryDate(conversationId: String): String?

    /**
     * 전체 삭제 - 로그아웃/로그인/회원가입 시 계정 전환 캐시 정리용
     * (서버 /end 응답으로 재생성 가능한 매핑이라 삭제해도 안전)
     */
    @Query("DELETE FROM conversation_diary_link")
    suspend fun deleteAll()
}
