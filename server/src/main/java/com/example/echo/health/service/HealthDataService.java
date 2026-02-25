package com.example.echo.health.service;

import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.entity.HealthLog;
import com.example.echo.health.repository.HealthLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthDataService {

    private final HealthLogRepository healthLogRepository;

    /**
     * 오늘 건강 데이터 조회
     */
    public HealthData getTodayHealthData(Long userId) {
        return healthLogRepository
                .findByUserIdAndRecordedDate(userId, LocalDate.now())
                .map(HealthLog::toHealthData)
                .orElse(createDefaultHealthData());
    }

    /**
     * 특정 날짜 건강 데이터 조회
     */
    public HealthData getHealthDataByDate(Long userId, LocalDate date) {
        return healthLogRepository
                .findByUserIdAndRecordedDate(userId, date)
                .map(HealthLog::toHealthData)
                .orElse(null);
    }

    /**
     * 오늘의 Enriched 건강 데이터 조회 (원시 데이터 + 7일 평균 + 평가 결과 + 포맷팅)
     *
     * @param userId 사용자 ID
     * @param preferredSleepHours 선호 수면 시간 (시간), null이면 수면 평가 생략
     * @return EnrichedHealthData
     */
    public EnrichedHealthData getEnrichedHealthData(Long userId, Integer preferredSleepHours) {
        // 1. 오늘 원시 건강 데이터 조회
        HealthData healthData = getTodayHealthData(userId);

        // 2. 7일 평균 조회
        Double avgSteps = getWeeklyAverageSteps(userId);
        Double avgSleepHours = getWeeklyAverageSleepHours(userId);
        LocalTime avgWakeUpTime = getWeeklyAverageWakeTime(userId);

        // 3. 평가 계산
        String stepsEvaluation = "";
        String sleepEvaluation = "";
        String wakeTimeEvaluation = "";

        if (healthData.getSteps() != null) {
            stepsEvaluation = evaluateSteps(healthData.getSteps(), avgSteps);
        }

        if (healthData.getSleepDurationMinutes() != null && preferredSleepHours != null) {
            sleepEvaluation = evaluateSleep(healthData.getSleepDurationMinutes(), preferredSleepHours);
        }

        if (healthData.getWakeUpTime() != null) {
            wakeTimeEvaluation = evaluateWakeTime(healthData.getWakeUpTime(), avgWakeUpTime);
        }

        // 4. EnrichedHealthData 생성 및 반환
        return EnrichedHealthData.builder()
                // 원시 데이터
                .steps(healthData.getSteps())
                .sleepDurationMinutes(healthData.getSleepDurationMinutes())
                .sleepStartTime(healthData.getSleepStartTime())
                .wakeUpTime(healthData.getWakeUpTime())
                .exerciseDistanceKm(healthData.getExerciseDistanceKm())
                .exerciseActivity(healthData.getExerciseActivity())
                .activityList(healthData.getActivityList())
                // 7일 평균
                .avgSteps(avgSteps)
                .avgSleepHours(avgSleepHours)
                .avgWakeUpTime(avgWakeUpTime)
                // 평가 결과
                .stepsEvaluation(stepsEvaluation)
                .sleepEvaluation(sleepEvaluation)
                .wakeTimeEvaluation(wakeTimeEvaluation)
                // 포맷팅된 문자열
                .stepsFormatted(formatSteps(healthData.getSteps()))
                .sleepDurationFormatted(formatSleepDuration(healthData.getSleepDurationMinutes()))
                .sleepStartTimeFormatted(formatTime(healthData.getSleepStartTime()))
                .wakeUpTimeFormatted(formatTime(healthData.getWakeUpTime()))
                .exerciseDistanceFormatted(formatExerciseDistance(healthData.getExerciseDistanceKm()))
                // 사용자 선호도
                .preferredSleepHours(preferredSleepHours)
                .build();
    }

    /**
     * 건강 데이터 저장
     */
    @Transactional
    public HealthLog saveHealthData(Long userId, HealthData data) {
        return saveHealthData(userId, LocalDate.now(), data);
    }

    /**
     * 특정 날짜의 건강 데이터 저장
     */
    @Transactional
    public HealthLog saveHealthData(Long userId, LocalDate date, HealthData data) {
        HealthLog healthLog = HealthLog.fromHealthData(userId, date, data);
        return healthLogRepository.save(healthLog);
    }

    /**
     * 7일 평균 걸음 수 조회
     */
    public Double getWeeklyAverageSteps(Long userId) {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(6);
        Double avg = healthLogRepository.findAverageStepsByUserIdAndDateRange(userId, startDate, endDate);
        return avg != null ? avg : 0.0;
    }

    /**
     * 7일 평균 수면 시간(시간) 조회
     */
    public Double getWeeklyAverageSleepHours(Long userId) {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(6);
        Double avgMinutes = healthLogRepository.findAverageSleepMinutesByUserIdAndDateRange(userId, startDate, endDate);
        return avgMinutes != null ? avgMinutes / 60.0 : 0.0;
    }

    /**
     * 수면 평가: 오늘 수면 vs 선호 수면 시간
     *
     * @param totalMinutes   오늘 수면 시간(분)
     * @param preferredHours 선호 수면 시간(시간)
     * @return "부족", "적당", "과도"
     */
    public String evaluateSleep(int totalMinutes, int preferredHours) {
        int preferredMinutes = preferredHours * 60;
        int diff = totalMinutes - preferredMinutes;

        if (diff < -30) return "부족";
        if (diff > 30) return "과도";
        return "적당";
    }

    /**
     * 걸음 수 평가: 오늘 걸음 vs 7일 평균
     *
     * @param todaySteps 오늘 걸음 수
     * @param avgSteps   7일 평균 걸음 수
     * @return "평소보다 많음", "평소와 비슷", "평소보다 적음", "데이터 없음"
     */
    public String evaluateSteps(int todaySteps, double avgSteps) {
        if (avgSteps == 0) return "데이터 없음";
        double ratio = todaySteps / avgSteps;

        if (ratio > 1.2) return "평소보다 많음";
        if (ratio < 0.8) return "평소보다 적음";
        return "평소와 비슷";
    }

    /**
     * 기상 시간 평가: 오늘 기상 vs 평균 기상 시간
     *
     * @param todayWakeTime 오늘 기상 시간
     * @param avgWakeTime   평균 기상 시간
     * @return "평소보다 일찍", "평소와 비슷", "평소보다 늦게"
     */
    public String evaluateWakeTime(LocalTime todayWakeTime, LocalTime avgWakeTime) {
        if (todayWakeTime == null || avgWakeTime == null) {
            return "데이터 없음";
        }
        long diffMinutes = Duration.between(avgWakeTime, todayWakeTime).toMinutes();

        if (diffMinutes < -15) return "평소보다 일찍";
        if (diffMinutes > 15) return "평소보다 늦게";
        return "평소와 비슷";
    }

    /**
     * 7일 평균 기상 시간 조회
     *
     * @param userId 사용자 ID
     * @return 평균 기상 시간 (데이터가 없으면 null)
     */
    public LocalTime getWeeklyAverageWakeTime(Long userId) {
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(6);

        java.util.List<LocalTime> wakeTimes = healthLogRepository
                .findWakeUpTimesByUserIdAndDateRange(userId, startDate, endDate);

        if (wakeTimes == null || wakeTimes.isEmpty()) {
            return null;
        }

        // LocalTime을 초로 변환하여 평균 계산 후 다시 LocalTime으로 변환
        long totalSeconds = wakeTimes.stream()
                .mapToLong(LocalTime::toSecondOfDay)
                .sum();
        long avgSeconds = totalSeconds / wakeTimes.size();

        return LocalTime.ofSecondOfDay(avgSeconds);
    }

    /**
     * 기본 건강 데이터 생성 (데이터 없을 때)
     */
    private HealthData createDefaultHealthData() {
        return HealthData.builder()
                .steps(null)
                .sleepDurationMinutes(null)
                .sleepStartTime(null)
                .wakeUpTime(null)
                .exerciseDistanceKm(null)
                .exerciseActivity(null)
                .activityList(null)
                .build();
    }

    // ========== 포맷팅 헬퍼 메서드 ==========

    /**
     * 걸음 수 포맷팅: "5,000보"
     */
    private String formatSteps(Integer steps) {
        if (steps == null) return "";
        return String.format("%,d보", steps);
    }

    /**
     * 수면 시간 포맷팅: "7시간 30분"
     */
    private String formatSleepDuration(Integer minutes) {
        if (minutes == null) return "";
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (mins == 0) {
            return hours + "시간";
        }
        return hours + "시간 " + mins + "분";
    }

    /**
     * 시간 포맷팅: "오전 7시 30분"
     */
    private String formatTime(LocalTime time) {
        if (time == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("a h시 m분");
        return time.format(formatter);
    }

    /**
     * 운동 거리 포맷팅: "2.5km"
     */
    private String formatExerciseDistance(Double distanceKm) {
        if (distanceKm == null) return "";
        return String.format("%.1fkm", distanceKm);
    }
}
