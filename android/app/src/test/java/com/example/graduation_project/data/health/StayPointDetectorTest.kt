package com.example.graduation_project.data.health

import com.example.graduation_project.domain.model.LocationPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StayPointDetectorTest {

    private val detector = StayPointDetectorImpl()

    // 서울 시청 근처 좌표 (기준점)
    private val baseLat = 37.5665
    private val baseLng = 126.9780

    // 기준점에서 ~30m 이내 오프셋 (같은 클러스터)
    private val nearOffset = 0.00025  // 약 27m

    // 기준점에서 ~200m 이상 오프셋 (다른 클러스터)
    private val farOffset = 0.002     // 약 200m

    private fun instant(minutesFromEpoch: Long): Instant =
        Instant.ofEpochSecond(minutesFromEpoch * 60)

    private fun nearPoint(minutesFromEpoch: Long, latDelta: Double = 0.0, lngDelta: Double = 0.0) =
        LocationPoint(baseLat + latDelta, baseLng + lngDelta, instant(minutesFromEpoch))

    @Test
    fun `빈_리스트_반환`() {
        val result = detector.detect(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `좌표_1개_반환`() {
        val result = detector.detect(listOf(nearPoint(0)))
        assertTrue(result.isEmpty())
    }

    @Test
    fun `전체_이동_체류없음`() {
        // 각 포인트가 이전 포인트에서 200m 이상 떨어져 있고 1분씩 이동
        val locations = listOf(
            LocationPoint(37.5665, 126.9780, instant(0)),
            LocationPoint(37.5685, 126.9800, instant(1)),
            LocationPoint(37.5705, 126.9820, instant(2)),
            LocationPoint(37.5725, 126.9840, instant(3))
        )
        val result = detector.detect(locations)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `전체_체류_하나의_StayPoint_반환`() {
        // 모든 포인트가 반경 50m 이내, 총 15분 체류
        val locations = listOf(
            nearPoint(0),
            nearPoint(5, nearOffset, 0.0),
            nearPoint(10, 0.0, nearOffset),
            nearPoint(15, nearOffset, nearOffset)
        )
        val result = detector.detect(locations)
        assertEquals(1, result.size)
        assertEquals(15, result[0].durationMinutes)
    }

    @Test
    fun `정상_체류지점_감지_centroid_검증`() {
        // 0~15분: 기준점 근처 체류 (centroid 검증)
        // 16분: 200m 이상 이동
        val clusterLats = listOf(baseLat, baseLat + nearOffset, baseLat - nearOffset)
        val clusterLngs = listOf(baseLng, baseLng + nearOffset, baseLng - nearOffset)
        val locations = listOf(
            LocationPoint(clusterLats[0], clusterLngs[0], instant(0)),
            LocationPoint(clusterLats[1], clusterLngs[1], instant(7)),
            LocationPoint(clusterLats[2], clusterLngs[2], instant(15)),
            LocationPoint(baseLat + farOffset, baseLng + farOffset, instant(16))
        )
        val result = detector.detect(locations)
        assertEquals(1, result.size)

        val expectedLat = clusterLats.average()
        val expectedLng = clusterLngs.average()
        assertEquals(expectedLat, result[0].latitude, 1e-10)
        assertEquals(expectedLng, result[0].longitude, 1e-10)
        assertEquals(15, result[0].durationMinutes)
    }

    @Test
    fun `마지막_구간이_체류인_경우`() {
        // 0분: 이동 시작, 1분: 먼 곳으로 이동, 5~20분: 마지막 장소 체류
        val locations = listOf(
            LocationPoint(baseLat, baseLng, instant(0)),
            LocationPoint(baseLat + farOffset, baseLng + farOffset, instant(5)),
            nearPoint(10, farOffset, farOffset),
            nearPoint(15, farOffset + nearOffset, farOffset),
            nearPoint(20, farOffset, farOffset + nearOffset)
        )
        val result = detector.detect(locations)
        assertEquals(1, result.size)
        assertEquals(15, result[0].durationMinutes)
    }

    @Test
    fun `최소_체류시간_미달_감지_안됨`() {
        // 5분 체류: MIN_STAY_DURATION(10분) 미달
        val locations = listOf(
            nearPoint(0),
            nearPoint(5),
            LocationPoint(baseLat + farOffset, baseLng + farOffset, instant(6))
        )
        val result = detector.detect(locations)
        assertTrue(result.isEmpty())
    }
}
