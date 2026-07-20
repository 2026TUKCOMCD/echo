package com.example.graduation_project.data.repository

import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.DiaryApi
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.local.dao.DiaryDao
import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.data.model.Diary
import kotlinx.coroutines.flow.Flow

/**
 * 일기 저장소
 *
 * 서버(GET /api/diaries)에서 일기를 받아 Room에 캐시하고,
 * 일기 탭은 캐시(Flow)를 구독 - 오프라인에서도 마지막 동기화 상태를 열람 가능
 *
 * 동기화 시점: 일기 탭 진입 시 + 대화 종료 시
 */
class DiaryRepository(
    private val diaryDao: DiaryDao,
    private val diaryApi: DiaryApi = ApiClient.diaryApi
) {

    /**
     * 서버에서 최근 N일 일기를 받아 로컬 캐시 갱신
     * 실패 시 캐시는 그대로 유지됨 (호출부에서 에러 표시)
     */
    suspend fun refresh(days: Int = 30): ApiResult<Unit> {
        return when (val result = safeApiCall { diaryApi.getDiaries(days) }) {
            is ApiResult.Success -> {
                diaryDao.upsertAll(result.data.map { it.toEntity() })
                ApiResult.Success(Unit)
            }
            is ApiResult.Error -> result
        }
    }

    /**
     * 로컬 캐시의 일기 목록 구독 (최신 날짜순)
     */
    fun observeDiaries(): Flow<List<DiaryEntity>> = diaryDao.observeAll()

    /**
     * 특정 날짜의 캐시된 일기 조회
     */
    suspend fun getDiaryByDate(date: String): DiaryEntity? = diaryDao.getByDate(date)

    private fun Diary.toEntity() = DiaryEntity(
        date = date,
        serverId = id,
        title = title,
        content = content,
        status = status,
        failureReason = failureReason,
        weather = weather,
        mood = mood,
        updatedAt = updatedAt
    )
}
