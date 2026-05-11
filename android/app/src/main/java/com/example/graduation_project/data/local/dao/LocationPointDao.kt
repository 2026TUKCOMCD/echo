package com.example.graduation_project.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.graduation_project.data.local.entity.LocationPointEntity

/**
 * 위치 포인트 DAO
 *
 * GPS 위치 데이터의 저장, 조회, 삭제 담당.
 */
@Dao
interface LocationPointDao {

    /**
     * 위치 포인트 저장
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(locationPoint: LocationPointEntity)

    /**
     * 여러 위치 포인트 일괄 저장
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locationPoints: List<LocationPointEntity>)

    /**
     * 특정 날짜의 위치 포인트 조회 (시간순 정렬)
     *
     * @param date YYYY-MM-DD 형식
     */
    @Query("SELECT * FROM location_points WHERE date = :date ORDER BY timestamp ASC")
    suspend fun getByDate(date: String): List<LocationPointEntity>

    /**
     * 오늘 수집된 위치 포인트 개수
     */
    @Query("SELECT COUNT(*) FROM location_points WHERE date = :date")
    suspend fun countByDate(date: String): Int

    /**
     * 특정 날짜의 위치 포인트 삭제
     */
    @Query("DELETE FROM location_points WHERE date = :date")
    suspend fun deleteByDate(date: String)

    /**
     * N일 이전 데이터 삭제 (저장 공간 관리)
     */
    @Query("DELETE FROM location_points WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    /**
     * 모든 위치 포인트 삭제
     */
    @Query("DELETE FROM location_points")
    suspend fun deleteAll()
}
