package com.example.graduation_project.data.location

import com.example.graduation_project.domain.location.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class StayPointDetectorImplTest {

    private lateinit var detector: StayPointDetectorImpl

    private val baseTime: Instant = Instant.parse("2024-01-01T09:00:00Z")

    private fun time(plusMinutes: Long): Instant = baseTime.plusSeconds(plusMinutes * 60)

    // 서울 시청 근처 기준 좌표 (anchor 기준 ~26m 이내)
    private fun anchorPoint(plusMinutes: Long) = LocationPoint(37.5665, 126.9780, time(plusMinutes))
    private fun nearPoint(plusMinutes: Long) = LocationPoint(37.5667, 126.9782, time(plusMinutes))
    private fun farPoint(plusMinutes: Long) = LocationPoint(37.5700, 126.9850, time(plusMinutes))

    @Before
    fun setUp() {
        detector = StayPointDetectorImpl()
    }

    // ─────────────────────────────────────────────
    // 팀원 기존 TC
    // ─────────────────────────────────────────────

    @Test
    fun `체류 지점 정상 감지`() {
        val locations = listOf(
            LocationPoint(37.88, 127.73, Instant.parse("2024-01-01T09:00:00Z")),
            LocationPoint(37.8801, 127.7301, Instant.parse("2024-01-01T09:10:00Z")),
            LocationPoint(37.8802, 127.7302, Instant.parse("2024-01-01T09:20:00Z")),
            LocationPoint(37.8803, 127.7303, Instant.parse("2024-01-01T09:30:00Z")),
            LocationPoint(37.90, 127.75, Instant.parse("2024-01-01T09:45:00Z"))
        )

        val stayPoints = detector.detect(locations)

        assertEquals(1, stayPoints.size)
        assertEquals(30, stayPoints[0].durationMinutes)
    }

    @Test
    fun `빈 좌표 리스트 - 빈 결과`() {
        val stayPoints = detector.detect(emptyList())
        assertTrue(stayPoints.isEmpty())
    }

    @Test
    fun `체류 없이 이동만 - 빈 결과`() {
        val locations = listOf(
            LocationPoint(37.88, 127.73, Instant.parse("2024-01-01T09:00:00Z")),
            LocationPoint(37.89, 127.74, Instant.parse("2024-01-01T09:05:00Z")),
            LocationPoint(37.90, 127.75, Instant.parse("2024-01-01T09:10:00Z"))
        )

        val stayPoints = detector.detect(locations)

        assertTrue(stayPoints.isEmpty())
    }

    @Test
    fun `9분 체류 - 감지 안됨`() {
        val locations = listOf(
            anchorPoint(0),
            nearPoint(3),
            nearPoint(6),
            nearPoint(9),
            farPoint(10)
        )

        val stayPoints = detector.detect(locations)

        assertTrue(stayPoints.isEmpty())
    }

    @Test
    fun `10분 체류 - 감지됨 (경계값)`() {
        val locations = listOf(
            anchorPoint(0),
            nearPoint(5),
            nearPoint(10),
            farPoint(11)
        )

        val stayPoints = detector.detect(locations)

        assertEquals(1, stayPoints.size)
        assertEquals(10, stayPoints[0].durationMinutes)
    }

    // ─────────────────────────────────────────────
    // 추가 TC
    // ─────────────────────────────────────────────

    @Test
    fun `유효하지 않은 좌표 포함 - 유효 포인트만으로 연산`() {
        val locations = listOf(
            anchorPoint(0),
            LocationPoint(Double.NaN, 126.9780, time(5)),
            LocationPoint(999.0, 126.9780, time(6)),
            nearPoint(10),
            farPoint(11)
        )

        val stayPoints = detector.detect(locations)

        assertEquals(1, stayPoints.size)
        assertEquals(10, stayPoints[0].durationMinutes)
    }

    @Test
    fun `모두 유효하지 않은 좌표 - 빈 결과`() {
        val locations = listOf(
            LocationPoint(Double.NaN, 126.9780, time(0)),
            LocationPoint(999.0, 126.9780, time(5)),
            LocationPoint(37.5665, Double.NaN, time(10))
        )

        val stayPoints = detector.detect(locations)

        assertTrue(stayPoints.isEmpty())
    }

    @Test
    fun `복수 체류 지점 - 체류 이동 체류`() {
        val locations = listOf(
            anchorPoint(0),
            nearPoint(10),
            nearPoint(20),
            farPoint(21),
            LocationPoint(37.5700, 126.9850, time(22)),
            LocationPoint(37.5702, 126.9852, time(32)),
            LocationPoint(37.5701, 126.9851, time(42))
        )

        val stayPoints = detector.detect(locations)

        assertEquals(2, stayPoints.size)
        assertEquals(20, stayPoints[0].durationMinutes)
        // 2번째 클러스터 시작 = farPoint(T+21), 종료 = T+42 → 21분
        assertEquals(21, stayPoints[1].durationMinutes)
    }

    @Test
    fun `GPS 갭 발생 - 양쪽 모두 MIN_STAY 미만`() {
        val locations = listOf(
            anchorPoint(0),
            nearPoint(5),
            nearPoint(5).copy(timestamp = time(40)),
            nearPoint(44)
        )

        val stayPoints = detector.detect(locations)

        assertTrue(stayPoints.isEmpty())
    }

    @Test
    fun `GPS 갭 발생 - 갭 전만 MIN_STAY 이상`() {
        val locations = listOf(
            anchorPoint(0),
            nearPoint(5),
            nearPoint(15),
            nearPoint(15).copy(timestamp = time(50)),
            nearPoint(54)
        )

        val stayPoints = detector.detect(locations)

        assertEquals(1, stayPoints.size)
        assertEquals(15, stayPoints[0].durationMinutes)
    }

    @Test
    fun `GPS 갭 정확히 30분 - flush 발생 안 함`() {
        val locations = listOf(
            anchorPoint(0),
            nearPoint(5),
            nearPoint(5).copy(timestamp = time(35)),  // 갭 = 정확히 30분
            nearPoint(45),
            farPoint(46)
        )

        val stayPoints = detector.detect(locations)

        assertEquals(1, stayPoints.size)
        assertEquals(45, stayPoints[0].durationMinutes)
    }

    @Test
    fun `GPS 갭 30분 1초 초과 - flush 발생`() {
        val t15 = time(15)
        val locations = listOf(
            anchorPoint(0),
            nearPoint(15),
            nearPoint(15).copy(timestamp = t15.plusSeconds(30 * 60 + 1)),
            nearPoint(15).copy(timestamp = t15.plusSeconds(30 * 60 + 1 + 5 * 60))
        )

        val stayPoints = detector.detect(locations)

        assertEquals(1, stayPoints.size)
        assertEquals(15, stayPoints[0].durationMinutes)
    }

    @Test
    fun `마지막 세그먼트 체류 - handleLastSegment 처리`() {
        val locations = listOf(
            farPoint(0),
            anchorPoint(1),
            nearPoint(11),
            nearPoint(21)
        )

        val stayPoints = detector.detect(locations)

        assertEquals(1, stayPoints.size)
        assertEquals(20, stayPoints[0].durationMinutes)
    }

    @Test
    fun `centroid 계산 정확도`() {
        // 모든 포인트가 anchor(37.5665, 126.9780)로부터 ~22m 이내에 위치
        // lat 0.0002° ≈ 22m, lng 0.0002° ≈ 18m (위도 37.5° 기준)
        val p1 = LocationPoint(37.5665, 126.9780, time(0))   // anchor
        val p2 = LocationPoint(37.5667, 126.9780, time(3))   // 22m 북
        val p3 = LocationPoint(37.5665, 126.9782, time(6))   // 18m 동
        val p4 = LocationPoint(37.5663, 126.9780, time(9))   // 22m 남
        val p5 = LocationPoint(37.5665, 126.9778, time(12))  // 18m 서
        val locations = listOf(p1, p2, p3, p4, p5, farPoint(13))

        val stayPoints = detector.detect(locations)

        assertEquals(1, stayPoints.size)
        val expectedLat = (p1.latitude + p2.latitude + p3.latitude + p4.latitude + p5.latitude) / 5
        val expectedLng = (p1.longitude + p2.longitude + p3.longitude + p4.longitude + p5.longitude) / 5
        assertEquals(expectedLat, stayPoints[0].latitude, 1e-6)
        assertEquals(expectedLng, stayPoints[0].longitude, 1e-6)
    }

    @Test
    fun `Haversine 거리 계산 정확도`() {
        // 위도 0.001° ≈ 111.3m
        val p1 = LocationPoint(37.5665, 126.9780, time(0))
        val p2 = LocationPoint(37.5675, 126.9780, time(1))

        val distance = detector.haversineDistance(p1, p2)

        assertEquals(111.3, distance, 1.0)
    }
}
