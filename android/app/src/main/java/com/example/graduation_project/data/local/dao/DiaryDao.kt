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
}
