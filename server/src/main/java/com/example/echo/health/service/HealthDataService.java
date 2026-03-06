package com.example.echo.health.service;

import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.entity.HealthLog;
import com.example.echo.health.repository.HealthLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
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
     * EnrichedHealthData 생성 (7일치 한번에 조회 후 서버에서 평균 계산)
     *
     * DB 접근 최적화: 기존 개별 평균 쿼리 3회 → 7일치 배치 조회 1회
     *
     * @param todayData 오늘 건강 데이터 (앱에서 전송받은 데이터)
     * @param userId 사용자 ID
     * @param preferredSleepHours 선호 수면 시간 (시간), null이면 수면 평가 생략
     * @return EnrichedHealthData
     */
    public EnrichedHealthData buildEnrichedHealthData(HealthData todayData, Long userId, Integer preferredSleepHours) {
        // 1. 7일치 데이터 한번에 조회
        LocalDate endDate = LocalDate.now().minusDays(1);
        LocalDate startDate = endDate.minusDays(6);
        List<HealthLog> weeklyLogs = healthLogRepository
                .findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(userId, startDate, endDate);

        log.debug("7일치 건강 데이터 조회 - userId: {}, 조회 기간: {} ~ {}, 데이터 수: {}",
                userId, startDate, endDate, weeklyLogs.size());

        // 2. 서버에서 평균 계산
        Double avgSteps = calculateAverageSteps(weeklyLogs);
        Double avgSleepHours = calculateAverageSleepHours(weeklyLogs);
        LocalTime avgWakeUpTime = calculateAverageWakeTime(weeklyLogs);

        // 3. 평가 계산
        String stepsEvaluation = "";
        String sleepEvaluation = "";
        String wakeTimeEvaluation = "";

        if (todayData != null && todayData.getSteps() != null) {
            stepsEvaluation = evaluateSteps(todayData.getSteps(), avgSteps);
        }

        if (todayData != null && todayData.getSleepDurationMinutes() != null && preferredSleepHours != null) {
            sleepEvaluation = evaluateSleep(todayData.getSleepDurationMinutes(), preferredSleepHours);
        }

        if (todayData != null && todayData.getWakeUpTime() != null) {
            wakeTimeEvaluation = evaluateWakeTime(todayData.getWakeUpTime(), avgWakeUpTime);
        }

        // 4. EnrichedHealthData 생성 및 반환
        return EnrichedHealthData.builder()
                // 원시 데이터
                .steps(todayData != null ? todayData.getSteps() : null)
                .sleepDurationMinutes(todayData != null ? todayData.getSleepDurationMinutes() : null)
                .sleepStartTime(todayData != null ? todayData.getSleepStartTime() : null)
                .wakeUpTime(todayData != null ? todayData.getWakeUpTime() : null)
                .exerciseDistanceKm(todayData != null ? todayData.getExerciseDistanceKm() : null)
                .exerciseActivity(todayData != null ? todayData.getExerciseActivity() : null)
                .activityList(todayData != null ? todayData.getActivityList() : null)
                // 7일 평균
                .avgSteps(avgSteps)
                .avgSleepHours(avgSleepHours)
                .avgWakeUpTime(avgWakeUpTime)
                // 평가 결과
                .stepsEvaluation(stepsEvaluation)
                .sleepEvaluation(sleepEvaluation)
                .wakeTimeEvaluation(wakeTimeEvaluation)
                // 포맷팅된 문자열
                .stepsFormatted(todayData != null ? formatSteps(todayData.getSteps()) : "")
                .sleepDurationFormatted(todayData != null ? formatSleepDuration(todayData.getSleepDurationMinutes()) : "")
                .sleepStartTimeFormatted(todayData != null ? formatTime(todayData.getSleepStartTime()) : "")
                .wakeUpTimeFormatted(todayData != null ? formatTime(todayData.getWakeUpTime()) : "")
                .exerciseDistanceFormatted(todayData != null ? formatExerciseDistance(todayData.getExerciseDistanceKm()) : "")
                // 사용자 선호도
                .preferredSleepHours(preferredSleepHours)
                .build();
    }

    /**
     * 건강 데이터 저장 또는 업데이트 (UPSERT)
     *
     * 같은 날짜에 데이터가 이미 존재하면 업데이트, 없으면 새로 생성
     * 대화 시작 시 즉시 호출하여 건강 데이터 손실 방지
     *
     * @param userId 사용자 ID
     * @param data 건강 데이터
     */
    @Transactional
    public void saveOrUpdateHealthData(Long userId, HealthData data) {
        if (data == null) {
            log.debug("건강 데이터가 null이므로 저장 생략 - userId: {}", userId);
            return;
        }

        LocalDate today = LocalDate.now();
        healthLogRepository.findByUserIdAndRecordedDate(userId, today)
                .ifPresentOrElse(
                        existing -> {
                            log.debug("기존 건강 데이터 업데이트 - userId: {}, date: {}", userId, today);
                            existing.update(data);
                        },
                        () -> {
                            log.debug("새 건강 데이터 생성 - userId: {}, date: {}", userId, today);
                            healthLogRepository.save(HealthLog.fromHealthData(userId, today, data));
                        }
                );
    }

    // ========== 서버 측 평균 계산 메서드 ==========

    /**
     * 7일치 로그에서 평균 걸음 수 계산
     */
    private Double calculateAverageSteps(List<HealthLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0.0;
        }
        return logs.stream()
                .filter(log -> log.getSteps() != null)
                .mapToInt(HealthLog::getSteps)
                .average()
                .orElse(0.0);
    }

    /**
     * 7일치 로그에서 평균 수면 시간(시간) 계산
     */
    private Double calculateAverageSleepHours(List<HealthLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return 0.0;
        }
        return logs.stream()
                .filter(log -> log.getSleepDurationMinutes() != null)
                .mapToInt(HealthLog::getSleepDurationMinutes)
                .average()
                .orElse(0.0) / 60.0;
    }

    /**
     * 7일치 로그에서 평균 기상 시간 계산
     */
    private LocalTime calculateAverageWakeTime(List<HealthLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return null;
        }

        List<LocalTime> wakeTimes = logs.stream()
                .map(HealthLog::getWakeUpTime)
                .filter(time -> time != null)
                .toList();

        if (wakeTimes.isEmpty()) {
            return null;
        }

        long totalSeconds = wakeTimes.stream()
                .mapToLong(LocalTime::toSecondOfDay)
                .sum();
        long avgSeconds = totalSeconds / wakeTimes.size();

        return LocalTime.ofSecondOfDay(avgSeconds);
    }

    /**
     * 건강 데이터 저장 (first-wins, 테스트 환경용)
     * - 오늘 날짜 데이터가 이미 있으면 skip
     */
    @Transactional
    public HealthLog saveHealthData(Long userId, HealthData data) {
        return saveHealthData(userId, LocalDate.now(), data);
    }

    /**
     * 특정 날짜의 건강 데이터 저장 (first-wins, 테스트 환경용)
     * - 동일 (userId, date) 데이터가 이미 있으면 skip (덮어쓰지 않음)
     */
    @Transactional
    public HealthLog saveHealthData(Long userId, LocalDate date, HealthData data) {
        java.util.Optional<HealthLog> existing = healthLogRepository.findByUserIdAndRecordedDate(userId, date);
        if (existing.isPresent()) {
            log.debug("건강 데이터 이미 존재하므로 skip - userId: {}, date: {}", userId, date);
            return existing.get();
        }

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
