package com.example.graduation_project.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.graduation_project.data.local.entity.DiaryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 일기 캐시 데이터 접근 객체 (DAO)
 *
 * 서버 응답을 upsert로 갱신하고, 일기 탭은 Flow로 캐시를 구독
 */
@Dao
interface DiaryDao {

    /**
     * 일기 목록 저장 (같은 날짜는 덮어쓰기)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(diaries: List<DiaryEntity>)

    /**
     * 모든 일기 조회 (최신 날짜순)
     * - 일기 탭 목록에서 사용
     */
    @Query("SELECT * FROM diaries ORDER BY date DESC")
    fun observeAll(): Flow<List<DiaryEntity>>

    /**
     * 특정 날짜 일기 조회
     * - 일기 상세 화면에서 사용
     */
    @Query("SELECT * FROM diaries WHERE date = :date")
    suspend fun getByDate(date: String): DiaryEntity?

    /**
     * 날짜 범위(양끝 포함) 일기 조회 (최신 날짜순)
     * - 일기 탭 캘린더에서 현재 보는 달만 관찰할 때 사용
     */
    @Query("SELECT * FROM diaries WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun observeByDateRange(start: String, end: String): Flow<List<DiaryEntity>>

    /**
     * 전체 삭제 - 로그아웃/로그인/회원가입 시 계정 전환 캐시 정리용
     * (서버가 원본이라 삭제해도 다음 동기화 시 재수신됨)
     */
    @Query("DELETE FROM diaries")
    suspend fun deleteAll()
}
