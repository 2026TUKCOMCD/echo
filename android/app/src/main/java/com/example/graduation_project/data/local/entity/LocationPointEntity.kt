package com.example.graduation_project.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/**
 * GPS 위치 포인트 Entity
 *
 * 10분마다 수집된 위치 데이터를 저장.
 * 대화 시작 시 StayPoint 알고리즘으로 방문 장소 계산에 사용.
 */
@Entity(tableName = "location_points")
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** 위도 */
    val latitude: Double,

    /** 경도 */
    val longitude: Double,

    /** 수집 시각 (epoch milliseconds) */
    val timestamp: Long,

    /** 수집 날짜 (YYYY-MM-DD 형식, 쿼리 최적화용) */
    val date: String
)
