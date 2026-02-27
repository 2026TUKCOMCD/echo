package com.example.graduation_project.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.graduation_project.domain.health.HealthConnectAvailability
import com.example.graduation_project.domain.health.SleepSummary
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Health Connect SDK 초기화 및 가용성 확인 담당.
 * - checkAvailability()로 상태 확인
 * - NotInstalled 상태일 경우 openPlayStoreForHealthConnect()로 설치 유도
 */
class HealthConnectManager(private val context: Context) {

    companion object {
        private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"
        private const val PLAY_STORE_URI = "market://details?id=$HEALTH_CONNECT_PACKAGE"
        private const val PLAY_STORE_WEB_URI =
            "https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE"

        val REQUIRED_PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class)
        )
    }

    /**
     * Health Connect SDK 가용성 확인.
     */
    fun checkAvailability(): HealthConnectAvailability {
        return when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE ->
                HealthConnectAvailability.Available
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.NotInstalled
            else ->
                HealthConnectAvailability.NotSupported
        }
    }

    /**
     * 현재 부여된 권한이 REQUIRED_PERMISSIONS를 모두 포함하는지 확인.
     */
    suspend fun checkGrantedPermissions(): Boolean {
        val client = HealthConnectClient.getOrCreate(context)
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(REQUIRED_PERMISSIONS)
    }

    /**
     * 어제 정오(12:00) → 오늘 정오(12:00) 범위의 수면 데이터 읽기.
     * - minutes: 전체 세션 합산 수면 시간(분)
     * - startTime: 가장 긴 세션(주 수면)의 시작 시각 "HH:mm" → 서버 낮잠 분류용
     */
    suspend fun readYesterdaySleep(): SleepSummary {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val rangeStart = today.minusDays(1).atTime(LocalTime.NOON).atZone(zone).toInstant()
        val rangeEnd = today.atTime(LocalTime.NOON).atZone(zone).toInstant()

        val response = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, TimeRangeFilter.between(rangeStart, rangeEnd))
        )
        if (response.records.isEmpty()) return SleepSummary(null, null, null)

        val totalMinutes = response.records.sumOf { record ->
            (record.endTime.toEpochMilli() - record.startTime.toEpochMilli()) / 60_000L
        }.toInt()

        val mainSession = response.records.maxByOrNull { record ->
            record.endTime.toEpochMilli() - record.startTime.toEpochMilli()
        }
        val startTimeStr = mainSession?.let {
            val localTime = it.startTime.atZone(zone).toLocalTime()
            String.format("%02d:%02d", localTime.hour, localTime.minute)
        }
        val wakeUpTimeStr = mainSession?.let {
            val localTime = it.endTime.atZone(zone).toLocalTime()
            String.format("%02d:%02d", localTime.hour, localTime.minute)
        }

        return SleepSummary(minutes = totalMinutes, startTime = startTimeStr, wakeUpTime = wakeUpTimeStr)
    }

    /**
     * 오늘 자정(00:00) → 현재 범위의 걸음 수 합산.
     */
    suspend fun readTodaySteps(): Int? {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val startTime = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val endTime = Instant.now()

        val response = client.readRecords(
            ReadRecordsRequest(StepsRecord::class, TimeRangeFilter.between(startTime, endTime))
        )
        if (response.records.isEmpty()) return null

        return response.records.sumOf { it.count }.toInt()
    }

    /**
     * 최근 24시간 내 가장 최근 ExerciseSessionRecord 읽기.
     * 거리는 DistanceRecord 우선, 없으면 laps 폴백.
     */
    suspend fun readLatestExercise(): Pair<Double?, String?> {
        val client = HealthConnectClient.getOrCreate(context)
        val endTime = Instant.now()
        val startTime = endTime.minusSeconds(24 * 60 * 60L)

        val exerciseResponse = client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(startTime, endTime))
        )
        if (exerciseResponse.records.isEmpty()) return Pair(null, null)

        val latest = exerciseResponse.records.maxByOrNull { it.endTime }
            ?: return Pair(null, null)

        val distanceKm = readExerciseDistanceKm(client, latest.startTime, latest.endTime)
            ?: run {
                val meters = latest.laps.mapNotNull { it.length?.inMeters }.sum()
                if (meters > 0.0) meters / 1000.0 else null
            }

        return Pair(distanceKm, exerciseTypeToKorean(latest.exerciseType))
    }

    private suspend fun readExerciseDistanceKm(
        client: HealthConnectClient,
        startTime: Instant,
        endTime: Instant
    ): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(DistanceRecord::class, TimeRangeFilter.between(startTime, endTime))
        )
        if (response.records.isEmpty()) return null
        val totalKm = response.records.sumOf { it.distance.inKilometers }
        return if (totalKm > 0.0) totalKm else null
    }

    private fun exerciseTypeToKorean(type: Int): String = when (type) {
        ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "걷기"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "달리기"
        ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL -> "런닝머신"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "자전거"
        ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY -> "실내 자전거"
        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "수영"
        ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "등산"
        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "근력 운동"
        ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "요가"
        ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
        ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "일립티컬"
        ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "계단 오르기"
        ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "댄스"
        ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "골프"
        ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "테니스"
        ExerciseSessionRecord.EXERCISE_TYPE_BADMINTON -> "배드민턴"
        else -> "운동"
    }

    /**
     * NotInstalled 상태일 때 Play Store로 연결.
     * Play Store 앱이 없으면 브라우저 웹 링크로 폴백.
     */
    fun openPlayStoreForHealthConnect() {
        val playStoreIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(PLAY_STORE_URI)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val webIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(PLAY_STORE_WEB_URI)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resolved = context.packageManager.resolveActivity(playStoreIntent, 0)
        context.startActivity(if (resolved != null) playStoreIntent else webIntent)
    }
}
