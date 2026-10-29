package com.example.graduation_project.data.repository

import android.util.Log
import com.example.graduation_project.data.api.ApiResult
import com.example.graduation_project.data.api.DiaryApi
import com.example.graduation_project.data.local.dao.DiaryDao
import com.example.graduation_project.data.local.entity.DiaryEntity
import com.example.graduation_project.data.model.Diary
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.YearMonth

/**
 * 일기 서버 동기화 → Room 캐시 저장 검증
 * 핵심: 성공하면 서버 응답을 캐시에 반영, 실패하면 캐시를 건드리지 않는다(오프라인 열람 유지)
 */
class DiaryRepositoryTest {

    private val diaryDao: DiaryDao = mockk()
    private val diaryApi: DiaryApi = mockk()
    private val repository = DiaryRepository(diaryDao, diaryApi)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.w(any(), any<String>()) } returns 0
        coEvery { diaryDao.upsertAll(any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    @Test
    fun `refreshMonth는 그 달 1일부터 말일까지 요청하고 응답을 캐시에 저장한다`() = runTest {
        coEvery { diaryApi.getDiariesInRange("2028-02-01", "2028-02-29") } returns listOf(
            Diary(id = 1L, date = "2028-02-29", title = "윤일", content = "내용", updatedAt = "2028-02-29T21:00:00"),
            Diary(id = 2L, date = "2028-02-03", content = "이전 내용", status = "FAILED", failureReason = "LLM 오류")
        )

        val result = repository.refreshMonth(YearMonth.of(2028, 2))

        assertTrue(result is ApiResult.Success)
        coVerify {
            diaryDao.upsertAll(
                listOf(
                    DiaryEntity("2028-02-29", 1L, "윤일", "내용", "SUCCESS", null, null, null, "2028-02-29T21:00:00"),
                    DiaryEntity("2028-02-03", 2L, null, "이전 내용", "FAILED", "LLM 오류", null, null, null)
                )
            )
        }
    }

    @Test
    fun `refreshMonth가 네트워크 오류면 오류를 돌려주고 캐시는 건드리지 않는다`() = runTest {
        coEvery { diaryApi.getDiariesInRange(any(), any()) } throws IOException("offline")

        val result = repository.refreshMonth(YearMonth.of(2026, 9))

        assertTrue(result is ApiResult.Error)
        coVerify(exactly = 0) { diaryDao.upsertAll(any()) }
    }

    @Test
    fun `refresh는 최근 N일을 요청해 캐시에 저장하고, 실패하면 캐시를 건드리지 않는다`() = runTest {
        coEvery { diaryApi.getDiaries(30) } returns listOf(Diary(id = 3L, date = "2026-09-30", content = "오늘"))

        assertTrue(repository.refresh() is ApiResult.Success)
        coVerify { diaryDao.upsertAll(match { it.single().date == "2026-09-30" }) }

        coEvery { diaryApi.getDiaries(30) } throws IOException("offline")
        assertTrue(repository.refresh() is ApiResult.Error)
        coVerify(exactly = 1) { diaryDao.upsertAll(any()) }
    }
}
