package com.example.graduation_project.data.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.graduation_project.domain.health.HealthConnectAvailability
import com.example.graduation_project.domain.health.SleepSummary
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

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
    fun checkAvailability(): HealthConnectAvailability =
        mapSdkStatus(
            HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE),
            Build.VERSION.SDK_INT
        )

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
     * - 주 수면 세션(최장 세션) 하나만 요약 (계산 로직은 summarizeSleepSessions 참고)
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

        return summarizeSleepSessions(
            response.records.map { it.startTime to it.endTime },
            zone
        )
    }

    /**
     * 오늘 자정(00:00) → 현재 범위의 걸음 수 합산.
     * aggregate()는 여러 앱(워치+폰)이 기록한 중복 데이터를 자동 제거함
     * (raw 레코드 단순 합산 시 동일 걸음이 두 번 집계될 수 있음)
     */
    suspend fun readTodaySteps(): Int? {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val startTime = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val endTime = Instant.now()

        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        return response[StepsRecord.COUNT_TOTAL]?.toInt()
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
        // aggregate()로 중복 출처(워치+폰) 데이터 자동 제거
        val response = client.aggregate(
            AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
        )
        val totalKm = response[DistanceRecord.DISTANCE_TOTAL]?.inKilometers
        return if (totalKm != null && totalKm > 0.0) totalKm else null
    }

    /**
     * 오늘 자정(00:00) → 현재 범위의 모든 운동 활동 목록.
     * 중복 제거 후 쉼표 구분 한국어 문자열 반환. 레코드 없으면 null.
     */
    suspend fun readTodayActivityList(): String? {
        val client = HealthConnectClient.getOrCreate(context)
        val zone = ZoneId.systemDefault()
        val startTime = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val endTime = Instant.now()

        val response = client.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, TimeRangeFilter.between(startTime, endTime))
        )
        if (response.records.isEmpty()) return null

        return response.records
            .map { exerciseTypeToKorean(it.exerciseType) }
            .distinct()
            .joinToString(",")
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

    // TODO: ExerciseRoute GPS 데이터 활용 기능 추후 재도입 예정
    // 참고: feature/US5.5-healthconnect-route-tracking 브랜치, commit 1feb261
    // 삭제된 메서드: readTodayExerciseRoutes(), readExerciseSessionLocations()

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

/**
 * 수면 세션 목록에서 주 수면 세션(최장 세션) 하나를 골라 요약.
 * - 시간과 시작/기상 시각이 같은 세션을 가리키도록 해 서버의 낮잠/야간 분류와 일관성 유지
 * - 낮잠, 워치+폰 중복 세션이 야간 수면 시간에 합산되는 문제도 함께 방지
 */
internal fun summarizeSleepSessions(
    sessions: List<Pair<Instant, Instant>>,
    zone: ZoneId
): SleepSummary {
    val main = sessions.maxByOrNull { (start, end) -> end.toEpochMilli() - start.toEpochMilli() }
        ?: return SleepSummary(null, null, null)
    val (start, end) = main
    val minutes = ((end.toEpochMilli() - start.toEpochMilli()) / 60_000L).toInt()

    fun Instant.toHHmm(): String {
        val localTime = atZone(zone).toLocalTime()
        return String.format(Locale.ROOT, "%02d:%02d", localTime.hour, localTime.minute)
    }

    return SleepSummary(minutes = minutes, startTime = start.toHHmm(), wakeUpTime = end.toHHmm())
}

/**
 * SDK 상태 → 가용성 매핑.
 * SDK_UNAVAILABLE/PROVIDER_UPDATE_REQUIRED의 경계가 라이브러리 버전에 따라 애매하므로,
 * HC 앱 설치가 가능한 API 28(P) 이상에서는 미가용이면 무조건 설치/업데이트 유도로 처리.
 * (API 34+는 HC가 플랫폼 내장이라 항상 SDK_AVAILABLE)
 */
internal fun mapSdkStatus(status: Int, sdkInt: Int): HealthConnectAvailability = when {
    status == HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.Available
    sdkInt >= Build.VERSION_CODES.P -> HealthConnectAvailability.NotInstalled
    else -> HealthConnectAvailability.NotSupported
}
