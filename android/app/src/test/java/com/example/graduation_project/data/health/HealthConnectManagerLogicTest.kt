package com.example.graduation_project.data.health

import androidx.health.connect.client.HealthConnectClient
import com.example.graduation_project.domain.health.HealthConnectAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * HealthConnectManager의 순수 계산 로직 단위 테스트.
 * - summarizeSleepSessions: 주 수면 세션(최장 세션) 요약
 * - mapSdkStatus: SDK 상태 → 가용성 방어적 매핑
 */
class HealthConnectManagerLogicTest {

    private val zone: ZoneId = ZoneId.of("Asia/Seoul")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Instant =
        LocalDateTime.of(year, month, day, hour, minute).atZone(zone).toInstant()

    // ===== summarizeSleepSessions =====

    @Test
    fun `세션이 없으면 모든 필드가 null이다`() {
        val result = summarizeSleepSessions(emptyList(), zone)

        assertNull(result.minutes)
        assertNull(result.startTime)
        assertNull(result.wakeUpTime)
    }

    @Test
    fun `단일 야간 세션이면 그 세션의 시간과 시각을 보고한다`() {
        // 23:00 ~ 다음날 07:00 (8시간)
        val sessions = listOf(
            at(2026, 7, 3, 23, 0) to at(2026, 7, 4, 7, 0)
        )

        val result = summarizeSleepSessions(sessions, zone)

        assertEquals(480, result.minutes)
        assertEquals("23:00", result.startTime)
        assertEquals("07:00", result.wakeUpTime)
    }

    @Test
    fun `낮잠이 있어도 주 수면 세션만 보고한다`() {
        // 어제 오후 낮잠 3시간 + 야간 수면 6시간 → 야간(최장) 세션만 보고
        // (기존 버그: minutes에 낮잠이 합산되어 "야간에 9시간 잤다"로 왜곡됨)
        val sessions = listOf(
            at(2026, 7, 3, 14, 0) to at(2026, 7, 3, 17, 0),  // 낮잠 3시간
            at(2026, 7, 3, 23, 30) to at(2026, 7, 4, 5, 30)  // 야간 6시간
        )

        val result = summarizeSleepSessions(sessions, zone)

        assertEquals(360, result.minutes)
        assertEquals("23:30", result.startTime)
        assertEquals("05:30", result.wakeUpTime)
    }

    @Test
    fun `워치와 폰이 같은 밤을 중복 기록해도 최장 세션 하나만 보고한다`() {
        // 같은 밤을 두 출처가 기록 (워치가 조금 더 길게 기록)
        // (기존 버그: 두 세션이 합산되어 수면 시간이 약 2배로 보고됨)
        val sessions = listOf(
            at(2026, 7, 3, 23, 0) to at(2026, 7, 4, 6, 30),  // 워치: 7.5시간
            at(2026, 7, 3, 23, 10) to at(2026, 7, 4, 6, 20)  // 폰: 7시간 10분
        )

        val result = summarizeSleepSessions(sessions, zone)

        assertEquals(450, result.minutes)  // 7.5시간 (합산 아님)
        assertEquals("23:00", result.startTime)
        assertEquals("06:30", result.wakeUpTime)
    }

    // ===== mapSdkStatus =====

    @Test
    fun `SDK_AVAILABLE이면 Available이다`() {
        val result = mapSdkStatus(HealthConnectClient.SDK_AVAILABLE, 28)

        assertEquals(HealthConnectAvailability.Available, result)
    }

    @Test
    fun `API 28 이상에서 업데이트 필요 상태면 NotInstalled로 설치 유도한다`() {
        val result = mapSdkStatus(HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED, 28)

        assertEquals(HealthConnectAvailability.NotInstalled, result)
    }

    @Test
    fun `API 28 이상에서 SDK_UNAVAILABLE이어도 NotInstalled로 설치 유도한다`() {
        // 핵심 수정 검증: 기존에는 NotSupported로 분류되어 설치 유도 다이얼로그가 영영 안 떴음
        val result = mapSdkStatus(HealthConnectClient.SDK_UNAVAILABLE, 28)

        assertEquals(HealthConnectAvailability.NotInstalled, result)
    }

    @Test
    fun `API 28 미만이면 NotSupported다`() {
        val result = mapSdkStatus(HealthConnectClient.SDK_UNAVAILABLE, 27)

        assertEquals(HealthConnectAvailability.NotSupported, result)
    }
}
