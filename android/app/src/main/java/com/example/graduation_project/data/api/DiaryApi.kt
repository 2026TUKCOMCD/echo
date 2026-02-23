package com.example.graduation_project.data.api

import com.example.graduation_project.data.model.Diary
import retrofit2.http.GET
import retrofit2.http.Path

interface DiaryApi {

    @GET("/api/diaries")
    suspend fun getDiaries(): List<Diary>

    @GET("/api/diaries/{id}")
    suspend fun getDiary(@Path("id") id: Long): Diary
}
