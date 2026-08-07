package com.example.graduation_project.data.repository

import android.util.Log
import com.example.graduation_project.data.api.ApiClient
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.DiaryApi
import com.example.graduation_project.data.api.safeApiCall
import com.example.graduation_project.data.local.dao.DiaryDao
import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.data.model.Diary
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth
import java.time.format.DateTimeFormatter

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
     * 서버에서 지정한 달(1일~말일) 일기를 받아 로컬 캐시 갱신
     * 일기 탭 캘린더에서 달 이동 시 호출 - 실패 시 캐시는 그대로 유지됨
     */
    suspend fun refreshMonth(yearMonth: YearMonth): ApiResult<Unit> {
        val startDate = yearMonth.atDay(1).format(DATE_FORMATTER)
        val endDate = yearMonth.atEndOfMonth().format(DATE_FORMATTER)
        return when (val result = safeApiCall { diaryApi.getDiariesInRange(startDate, endDate) }) {
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
     * 지정한 달(1일~말일)의 캐시된 일기 구독 - 일기 탭 캘린더에서 사용
     */
    fun observeMonth(yearMonth: YearMonth): Flow<List<DiaryEntity>> {
        val startDate = yearMonth.atDay(1).format(DATE_FORMATTER)
        val endDate = yearMonth.atEndOfMonth().format(DATE_FORMATTER)
        return diaryDao.observeByDateRange(startDate, endDate)
    }

    /**
     * 특정 날짜의 캐시된 일기 조회
     */
    suspend fun getDiaryByDate(date: String): DiaryEntity? = diaryDao.getByDate(date)

    private fun Diary.toEntity(): DiaryEntity {
        if (status == "FAILED" && failureReason != null) {
            // UI는 원문 대신 안내 문구만 보여주므로, 실제 사유는 여기서만 남김
            Log.w(TAG, "일기 생성/갱신 실패: date=$date reason=$failureReason")
        }
        return DiaryEntity(
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

    companion object {
        private const val TAG = "DiaryRepository"
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    }
}
