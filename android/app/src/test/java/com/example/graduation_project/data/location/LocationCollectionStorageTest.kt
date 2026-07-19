package com.example.graduation_project.data.location

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocationCollectionStorageTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var storage: LocationCollectionStorage

    @Before
    fun setUp() {
        mockContext = mockk()
        mockPrefs = mockk()
        mockEditor = mockk(relaxed = true)

        every { mockContext.getSharedPreferences(any(), any()) } returns mockPrefs
        every { mockPrefs.edit() } returns mockEditor
        every { mockEditor.putString(any(), any()) } returns mockEditor
        every { mockEditor.putLong(any(), any()) } returns mockEditor

        storage = LocationCollectionStorage(mockContext)
    }

    // ===== 시작 시간 저장/조회 =====

    @Test
    fun `saveStartTime_정상_저장시_SharedPreferences에_저장됨`() {
        storage.saveStartTime("07:30")

        verify { mockEditor.putString("start_time", "07:30") }
        verify { mockEditor.apply() }
    }

    @Test
    fun `getStartTime_저장된_값이_있으면_해당_값_반환`() {
        every { mockPrefs.getString("start_time", "06:00") } returns "08:00"

        val result = storage.getStartTime()

        assertEquals("08:00", result)
    }

    @Test
    fun `getStartTime_저장된_값이_없으면_기본값_0600_반환`() {
        every { mockPrefs.getString("start_time", "06:00") } returns "06:00"

        val result = storage.getStartTime()

        assertEquals("06:00", result)
    }

    @Test
    fun `getStartHour_정상_시간에서_시_추출`() {
        every { mockPrefs.getString("start_time", "06:00") } returns "14:30"

        val result = storage.getStartHour()

        assertEquals(14, result)
    }

    @Test
    fun `getStartHour_잘못된_형식이면_기본값_6_반환`() {
        every { mockPrefs.getString("start_time", "06:00") } returns "invalid"

        val result = storage.getStartHour()

        assertEquals(6, result)
    }

    @Test
    fun `getStartMinute_정상_시간에서_분_추출`() {
        every { mockPrefs.getString("start_time", "06:00") } returns "14:45"

        val result = storage.getStartMinute()

        assertEquals(45, result)
    }

    @Test
    fun `getStartMinute_잘못된_형식이면_기본값_0_반환`() {
        every { mockPrefs.getString("start_time", "06:00") } returns "14"

        val result = storage.getStartMinute()

        assertEquals(0, result)
    }

    // ===== 마지막 수집 시간 =====

    @Test
    fun `saveLastCollectionTime_정상_저장`() {
        val timeMillis = 1700000000000L

        storage.saveLastCollectionTime(timeMillis)

        verify { mockEditor.putLong("last_collection_time", timeMillis) }
        verify { mockEditor.apply() }
    }

    @Test
    fun `getLastCollectionTime_저장된_값_반환`() {
        every { mockPrefs.getLong("last_collection_time", 0L) } returns 1700000000000L

        val result = storage.getLastCollectionTime()

        assertEquals(1700000000000L, result)
    }

    @Test
    fun `getLastCollectionTime_저장된_값_없으면_0_반환`() {
        every { mockPrefs.getLong("last_collection_time", 0L) } returns 0L

        val result = storage.getLastCollectionTime()

        assertEquals(0L, result)
    }

    // ===== 경과 시간 계산 =====

    @Test
    fun `getElapsedSinceLastCollection_정상_경과시간_계산`() {
        // 5분 전에 마지막 수집
        val fiveMinutesAgo = System.currentTimeMillis() - 5 * 60 * 1000
        every { mockPrefs.getLong("last_collection_time", 0L) } returns fiveMinutesAgo

        val result = storage.getElapsedSinceLastCollection()

        // 약 5분 (300,000ms) 정도의 경과 시간
        assert(result in 290_000..310_000) { "Expected ~300,000ms but got $result" }
    }

    @Test
    fun `getElapsedSinceLastCollection_수집_기록_없으면_MAX_VALUE_반환`() {
        every { mockPrefs.getLong("last_collection_time", 0L) } returns 0L

        val result = storage.getElapsedSinceLastCollection()

        assertEquals(Long.MAX_VALUE, result)
    }

    // ===== 경계 조건 =====

    @Test
    fun `getStartHour_빈_문자열이면_기본값_반환`() {
        every { mockPrefs.getString("start_time", "06:00") } returns ""

        val result = storage.getStartHour()

        assertEquals(6, result)
    }

    @Test
    fun `getStartHour_콜론만_있는_경우_기본값_반환`() {
        every { mockPrefs.getString("start_time", "06:00") } returns ":"

        val result = storage.getStartHour()

        assertEquals(6, result)
    }

    @Test
    fun `getStartMinute_숫자가_아닌_경우_기본값_반환`() {
        every { mockPrefs.getString("start_time", "06:00") } returns "12:abc"

        val result = storage.getStartMinute()

        assertEquals(0, result)
    }
}
