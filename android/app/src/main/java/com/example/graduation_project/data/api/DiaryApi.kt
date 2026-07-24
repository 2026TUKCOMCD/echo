package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.Diary
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DiaryApi {

    /**
     * 최근 N일 일기 목록 조회 (날짜 내림차순, 생성 실패 기록 포함)
     */
    @GET("/api/diaries")
    suspend fun getDiaries(@Query("days") days: Int = 30): List<Diary>

    /**
     * 날짜 범위(양끝 포함) 일기 목록 조회 (날짜 내림차순) - 캘린더 월 조회용
     */
    @GET("/api/diaries")
    suspend fun getDiariesInRange(
        @Query("startDate") startDate: String,
        @Query("endDate") endDate: String
    ): List<Diary>

    @GET("/api/diaries/{id}")
    suspend fun getDiary(@Path("id") id: Long): Diary
}
