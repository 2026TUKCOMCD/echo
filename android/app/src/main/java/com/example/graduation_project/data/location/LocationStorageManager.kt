package com.example.graduation_project.data.location

import android.util.Log
import com.example.graduation_project.data.local.dao.LocationPointDao
import com.example.graduation_project.data.local.entity.LocationPointEntity
import com.example.graduation_project.domain.model.LocationPoint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 위치 데이터 저장/조회 매니저
 *
 * Room DB를 통해 GPS 위치 데이터를 관리.
 * - 10분마다 수집된 위치 저장
 * - 오늘 수집된 위치 조회 (StayPoint 계산용)
 * - 오래된 데이터 자동 정리
 */
class LocationStorageManager(
    private val locationPointDao: LocationPointDao
) {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /**
     * 위치 포인트 저장
     */
    suspend fun saveLocation(latitude: Double, longitude: Double) {
        val now = Instant.now()
        val today = LocalDate.now().format(dateFormatter)

        val entity = LocationPointEntity(
            latitude = latitude,
            longitude = longitude,
            timestamp = now.toEpochMilli(),
            date = today
        )

        locationPointDao.insert(entity)
        Log.d(TAG, "위치 저장: lat=$latitude, lon=$longitude, date=$today")
    }

    /**
     * 오늘 수집된 위치 포인트 조회
     *
     * @return LocationPoint 리스트 (시간순 정렬)
     */
    suspend fun getTodayLocations(): List<LocationPoint> {
        val today = LocalDate.now().format(dateFormatter)
        val entities = locationPointDao.getByDate(today)

        Log.d(TAG, "오늘 위치 조회: ${entities.size}개")

        return entities.map { entity ->
            LocationPoint(
                latitude = entity.latitude,
                longitude = entity.longitude,
                timestamp = Instant.ofEpochMilli(entity.timestamp)
            )
        }
    }

    /**
     * 오늘 수집된 위치 포인트 개수
     */
    suspend fun getTodayLocationCount(): Int {
        val today = LocalDate.now().format(dateFormatter)
        return locationPointDao.countByDate(today)
    }

    /**
     * 오래된 데이터 정리 (7일 이전 데이터 삭제)
     */
    suspend fun cleanupOldData(daysToKeep: Int = 7) {
        val cutoffDate = LocalDate.now()
            .minusDays(daysToKeep.toLong())
            .format(dateFormatter)

        locationPointDao.deleteOlderThan(cutoffDate)
        Log.d(TAG, "$cutoffDate 이전 데이터 삭제 완료")
    }

    /**
     * 오늘 데이터 삭제 (대화 종료 후 정리용)
     */
    suspend fun clearTodayData() {
        val today = LocalDate.now().format(dateFormatter)
        locationPointDao.deleteByDate(today)
        Log.d(TAG, "오늘 위치 데이터 삭제 완료")
    }

    /**
     * 모든 위치 데이터 삭제
     */
    suspend fun clearAllData() {
        locationPointDao.deleteAll()
        Log.d(TAG, "모든 위치 데이터 삭제 완료")
    }

    companion object {
        private const val TAG = "LocationStorageManager"
    }
}
