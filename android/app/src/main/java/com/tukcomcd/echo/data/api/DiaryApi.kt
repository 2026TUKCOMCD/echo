package com.tukcomcd.echo.data.api

import com.tukcomcd.echo.data.model.Diary
import retrofit2.http.GET
import retrofit2.http.Path

interface DiaryApi {

    @GET("/api/diaries")
    suspend fun getDiaries(): List<Diary>

    @GET("/api/diaries/{id}")
    suspend fun getDiary(@Path("id") id: Long): Diary
}
