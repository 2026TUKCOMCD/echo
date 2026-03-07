package com.example.echo.health.service;

import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.entity.HealthLog;
import com.example.echo.health.repository.HealthLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthDataServiceTest {

    @Mock
    private HealthLogRepository healthLogRepository;

    @InjectMocks
    private HealthDataService healthDataService;

    private final Long TEST_USER_ID = 1L;

    @Nested
    @DisplayName("getTodayHealthData 테스트")
    class GetTodayHealthDataTest {

        @Test
        @DisplayName("데이터 존재 시 HealthData 반환")
        void getTodayHealthData_found() {
            // Given
            HealthLog healthLog = HealthLog.builder()
                    .userId(TEST_USER_ID)
                    .recordedDate(LocalDate.now())
                    .steps(5000)
                    .sleepDurationMinutes(420)
                    .sleepStartTime(LocalTime.of(23, 0))
                    .wakeUpTime(LocalTime.of(7, 0))
                    .exerciseDistanceKm(2.0)
                    .exerciseActivity("산책")
                    .build();

            when(healthLogRepository.findByUserIdAndRecordedDate(eq(TEST_USER_ID), any(LocalDate.class)))
                    .thenReturn(Optional.of(healthLog));

            // When
            HealthData result = healthDataService.getTodayHealthData(TEST_USER_ID);

            // Then
            assertThat(result.getSteps()).isEqualTo(5000);
            assertThat(result.getSleepDurationMinutes()).isEqualTo(420);
            assertThat(result.getExerciseActivity()).isEqualTo("산책");
        }

        @Test
        @DisplayName("데이터 없으면 기본 HealthData 반환 (모든 필드 null)")
        void getTodayHealthData_notFound() {
            // Given
            when(healthLogRepository.findByUserIdAndRecordedDate(eq(TEST_USER_ID), any(LocalDate.class)))
                    .thenReturn(Optional.empty());

            // When
            HealthData result = healthDataService.getTodayHealthData(TEST_USER_ID);

            // Then
            assertThat(result.getSteps()).isNull();
            assertThat(result.getSleepDurationMinutes()).isNull();
            assertThat(result.getExerciseDistanceKm()).isNull();
            assertThat(result.getExerciseActivity()).isNull();
        }
    }

    @Nested
    @DisplayName("saveHealthData 테스트")
    class SaveHealthDataTest {

        @Test
        @DisplayName("새 건강 데이터 저장 성공")
        void saveHealthData_new() {
            // Given
            HealthData healthData = HealthData.builder()
                    .steps(6000)
                    .sleepDurationMinutes(480)
                    .exerciseDistanceKm(3.0)
                    .exerciseActivity("조깅")
                    .build();

            when(healthLogRepository.findByUserIdAndRecordedDate(eq(TEST_USER_ID), any(LocalDate.class)))
                    .thenReturn(Optional.empty());
            when(healthLogRepository.save(any(HealthLog.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            HealthLog result = healthDataService.saveHealthData(TEST_USER_ID, healthData);

            // Then
            verify(healthLogRepository).save(any(HealthLog.class));
            assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(result.getSteps()).isEqualTo(6000);
        }

        @Test
        @DisplayName("기존 건강 데이터 업데이트 성공 (UPSERT)")
        void saveHealthData_update() {
            // Given
            HealthLog existingLog = HealthLog.builder()
                    .userId(TEST_USER_ID)
                    .recordedDate(LocalDate.now())
                    .steps(5000)
                    .sleepDurationMinutes(420)
                    .build();

            HealthData newHealthData = HealthData.builder()
                    .steps(8000)
                    .sleepDurationMinutes(540)
                    .exerciseDistanceKm(5.0)
                    .exerciseActivity("달리기")
                    .build();

            when(healthLogRepository.findByUserIdAndRecordedDate(eq(TEST_USER_ID), any(LocalDate.class)))
                    .thenReturn(Optional.of(existingLog));

            // When
            HealthLog result = healthDataService.saveHealthData(TEST_USER_ID, newHealthData);

            // Then
            assertThat(result).isSameAs(existingLog);
            assertThat(result.getSteps()).isEqualTo(8000);
            assertThat(result.getSleepDurationMinutes()).isEqualTo(540);
            assertThat(result.getExerciseActivity()).isEqualTo("달리기");
        }

        @Test
        @DisplayName("null 데이터 저장 시 null 반환")
        void saveHealthData_null() {
            // When
            HealthLog result = healthDataService.saveHealthData(TEST_USER_ID, null);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("evaluateSleep 테스트")
    class EvaluateSleepTest {

        @Test
        @DisplayName("수면 부족: 선호보다 30분 이상 적게 잠")
        void evaluateSleep_insufficient() {
            // 선호 7시간(420분), 실제 6시간(360분) - 차이 -60분
            assertThat(healthDataService.evaluateSleep(360, 7)).isEqualTo("부족");
        }

        @Test
        @DisplayName("수면 적당: 선호와 비슷하게 잠")
        void evaluateSleep_adequate() {
            // 선호 7시간(420분), 실제 7시간 20분(440분) - 차이 +20분
            assertThat(healthDataService.evaluateSleep(440, 7)).isEqualTo("적당");
        }

        @Test
        @DisplayName("수면 과도: 선호보다 30분 이상 많이 잠")
        void evaluateSleep_excessive() {
            // 선호 7시간(420분), 실제 8시간 30분(510분) - 차이 +90분
            assertThat(healthDataService.evaluateSleep(510, 7)).isEqualTo("과도");
        }

        @Test
        @DisplayName("경계값 테스트: 정확히 30분 차이")
        void evaluateSleep_boundary() {
            // -30분 차이: 적당
            assertThat(healthDataService.evaluateSleep(390, 7)).isEqualTo("적당");
            // +30분 차이: 적당
            assertThat(healthDataService.evaluateSleep(450, 7)).isEqualTo("적당");
            // -31분 차이: 부족
            assertThat(healthDataService.evaluateSleep(389, 7)).isEqualTo("부족");
            // +31분 차이: 과도
            assertThat(healthDataService.evaluateSleep(451, 7)).isEqualTo("과도");
        }
    }

    @Nested
    @DisplayName("evaluateSteps 테스트")
    class EvaluateStepsTest {

        @Test
        @DisplayName("평소보다 많음: 평균의 120% 초과")
        void evaluateSteps_more() {
            // 평균 5000, 오늘 6500 (130%)
            assertThat(healthDataService.evaluateSteps(6500, 5000)).isEqualTo("평소보다 많음");
        }

        @Test
        @DisplayName("평소와 비슷: 평균의 80%~120%")
        void evaluateSteps_similar() {
            // 평균 5000, 오늘 5000 (100%)
            assertThat(healthDataService.evaluateSteps(5000, 5000)).isEqualTo("평소와 비슷");
        }

        @Test
        @DisplayName("평소보다 적음: 평균의 80% 미만")
        void evaluateSteps_less() {
            // 평균 5000, 오늘 3500 (70%)
            assertThat(healthDataService.evaluateSteps(3500, 5000)).isEqualTo("평소보다 적음");
        }

        @Test
        @DisplayName("데이터 없음: 평균이 0일 때")
        void evaluateSteps_noData() {
            assertThat(healthDataService.evaluateSteps(5000, 0)).isEqualTo("데이터 없음");
        }

        @Test
        @DisplayName("경계값 테스트")
        void evaluateSteps_boundary() {
            // 정확히 80%: 비슷
            assertThat(healthDataService.evaluateSteps(4000, 5000)).isEqualTo("평소와 비슷");
            // 정확히 120%: 비슷
            assertThat(healthDataService.evaluateSteps(6000, 5000)).isEqualTo("평소와 비슷");
            // 79%: 적음
            assertThat(healthDataService.evaluateSteps(3950, 5000)).isEqualTo("평소보다 적음");
            // 121%: 많음
            assertThat(healthDataService.evaluateSteps(6050, 5000)).isEqualTo("평소보다 많음");
        }
    }

    @Nested
    @DisplayName("evaluateWakeTime 테스트")
    class EvaluateWakeTimeTest {

        @Test
        @DisplayName("평소보다 일찍: 평균보다 15분 이상 일찍 기상")
        void evaluateWakeTime_earlier() {
            LocalTime avgWakeTime = LocalTime.of(7, 0);
            LocalTime todayWakeTime = LocalTime.of(6, 30);
            assertThat(healthDataService.evaluateWakeTime(todayWakeTime, avgWakeTime))
                    .isEqualTo("평소보다 일찍");
        }

        @Test
        @DisplayName("평소와 비슷: 평균과 15분 이내 차이")
        void evaluateWakeTime_similar() {
            LocalTime avgWakeTime = LocalTime.of(7, 0);
            LocalTime todayWakeTime = LocalTime.of(7, 10);
            assertThat(healthDataService.evaluateWakeTime(todayWakeTime, avgWakeTime))
                    .isEqualTo("평소와 비슷");
        }

        @Test
        @DisplayName("평소보다 늦게: 평균보다 15분 이상 늦게 기상")
        void evaluateWakeTime_later() {
            LocalTime avgWakeTime = LocalTime.of(7, 0);
            LocalTime todayWakeTime = LocalTime.of(7, 30);
            assertThat(healthDataService.evaluateWakeTime(todayWakeTime, avgWakeTime))
                    .isEqualTo("평소보다 늦게");
        }

        @Test
        @DisplayName("데이터 없음: null 값 처리")
        void evaluateWakeTime_noData() {
            assertThat(healthDataService.evaluateWakeTime(null, LocalTime.of(7, 0)))
                    .isEqualTo("데이터 없음");
            assertThat(healthDataService.evaluateWakeTime(LocalTime.of(7, 0), null))
                    .isEqualTo("데이터 없음");
        }

        @Test
        @DisplayName("경계값 테스트")
        void evaluateWakeTime_boundary() {
            LocalTime avgWakeTime = LocalTime.of(7, 0);
            // 정확히 -15분: 비슷
            assertThat(healthDataService.evaluateWakeTime(LocalTime.of(6, 45), avgWakeTime))
                    .isEqualTo("평소와 비슷");
            // 정확히 +15분: 비슷
            assertThat(healthDataService.evaluateWakeTime(LocalTime.of(7, 15), avgWakeTime))
                    .isEqualTo("평소와 비슷");
            // -16분: 일찍
            assertThat(healthDataService.evaluateWakeTime(LocalTime.of(6, 44), avgWakeTime))
                    .isEqualTo("평소보다 일찍");
            // +16분: 늦게
            assertThat(healthDataService.evaluateWakeTime(LocalTime.of(7, 16), avgWakeTime))
                    .isEqualTo("평소보다 늦게");
        }
    }

    @Nested
    @DisplayName("getHealthDataByDate 테스트")
    class GetHealthDataByDateTest {

        @Test
        @DisplayName("특정 날짜 데이터 존재 시 HealthData 반환")
        void getHealthDataByDate_found() {
            // Given
            LocalDate targetDate = LocalDate.of(2026, 3, 1);
            HealthLog healthLog = HealthLog.builder()
                    .userId(TEST_USER_ID)
                    .recordedDate(targetDate)
                    .steps(8000)
                    .sleepDurationMinutes(480)
                    .wakeUpTime(LocalTime.of(6, 30))
                    .exerciseActivity("등산")
                    .build();

            when(healthLogRepository.findByUserIdAndRecordedDate(TEST_USER_ID, targetDate))
                    .thenReturn(Optional.of(healthLog));

            // When
            HealthData result = healthDataService.getHealthDataByDate(TEST_USER_ID, targetDate);

            // Then
            assertThat(result).isNotNull();
            assertThat(result.getSteps()).isEqualTo(8000);
            assertThat(result.getSleepDurationMinutes()).isEqualTo(480);
            assertThat(result.getExerciseActivity()).isEqualTo("등산");
        }

        @Test
        @DisplayName("특정 날짜 데이터 없으면 null 반환")
        void getHealthDataByDate_notFound() {
            // Given
            LocalDate targetDate = LocalDate.of(2026, 3, 1);
            when(healthLogRepository.findByUserIdAndRecordedDate(TEST_USER_ID, targetDate))
                    .thenReturn(Optional.empty());

            // When
            HealthData result = healthDataService.getHealthDataByDate(TEST_USER_ID, targetDate);

            // Then
            assertThat(result).isNull();
        }
    }

    @Nested
    @DisplayName("buildEnrichedHealthData 테스트")
    class BuildEnrichedHealthDataTest {

        @Test
        @DisplayName("정상 케이스: 7일 평균과 평가 결과 포함")
        void buildEnrichedHealthData_success() {
            // Given
            HealthData todayData = HealthData.builder()
                    .steps(6500) // 5000 * 1.3 = 6500 (130%, "평소보다 많음" 조건: > 120%)
                    .sleepDurationMinutes(420)
                    .wakeUpTime(LocalTime.of(7, 0))
                    .sleepStartTime(LocalTime.of(23, 0))
                    .exerciseDistanceKm(2.5)
                    .exerciseActivity("산책")
                    .build();

            List<HealthLog> weeklyLogs = List.of(
                    createHealthLog(5000, 400, LocalTime.of(7, 10)),
                    createHealthLog(5500, 420, LocalTime.of(7, 0)),
                    createHealthLog(4500, 380, LocalTime.of(6, 50))
            );

            when(healthLogRepository.findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(
                    eq(TEST_USER_ID), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(weeklyLogs);

            // When
            EnrichedHealthData result = healthDataService.buildEnrichedHealthData(
                    todayData, TEST_USER_ID, 7);

            // Then
            // 원시 데이터 확인
            assertThat(result.getSteps()).isEqualTo(6500);
            assertThat(result.getSleepDurationMinutes()).isEqualTo(420);
            assertThat(result.getWakeUpTime()).isEqualTo(LocalTime.of(7, 0));
            assertThat(result.getExerciseActivity()).isEqualTo("산책");

            // 7일 평균 확인
            assertThat(result.getAvgSteps()).isEqualTo(5000.0);
            assertThat(result.getAvgSleepHours()).isCloseTo(6.67, org.assertj.core.api.Assertions.within(0.01));
            assertThat(result.getAvgWakeUpTime()).isNotNull();

            // 평가 결과 확인
            assertThat(result.getStepsEvaluation()).isEqualTo("평소보다 많음"); // 6500/5000 = 1.3 > 1.2
            assertThat(result.getSleepEvaluation()).isEqualTo("적당"); // 420분 vs 7시간(420분)
            assertThat(result.getWakeTimeEvaluation()).isNotEmpty();

            // 포맷팅 확인
            assertThat(result.getStepsFormatted()).isEqualTo("6,500보");
            assertThat(result.getSleepDurationFormatted()).isEqualTo("7시간");
            assertThat(result.getExerciseDistanceFormatted()).isEqualTo("2.5km");
        }

        @Test
        @DisplayName("todayData가 null: 빈 EnrichedHealthData 반환")
        void buildEnrichedHealthData_nullTodayData() {
            // Given
            when(healthLogRepository.findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(
                    eq(TEST_USER_ID), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            EnrichedHealthData result = healthDataService.buildEnrichedHealthData(
                    null, TEST_USER_ID, 7);

            // Then
            assertThat(result.getSteps()).isNull();
            assertThat(result.getSleepDurationMinutes()).isNull();
            assertThat(result.getStepsEvaluation()).isEmpty();
            assertThat(result.getSleepEvaluation()).isEmpty();
        }

        @Test
        @DisplayName("7일 데이터 없음: 평균 0.0, 평가는 '데이터 없음'")
        void buildEnrichedHealthData_noWeeklyData() {
            // Given
            HealthData todayData = HealthData.builder()
                    .steps(5000)
                    .wakeUpTime(LocalTime.of(7, 0))
                    .build();

            when(healthLogRepository.findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(
                    eq(TEST_USER_ID), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            EnrichedHealthData result = healthDataService.buildEnrichedHealthData(
                    todayData, TEST_USER_ID, null);

            // Then
            assertThat(result.getAvgSteps()).isEqualTo(0.0);
            assertThat(result.getAvgSleepHours()).isEqualTo(0.0);
            assertThat(result.getAvgWakeUpTime()).isNull();
            assertThat(result.getStepsEvaluation()).isEqualTo("데이터 없음");
            assertThat(result.getWakeTimeEvaluation()).isEqualTo("데이터 없음");
        }

        @Test
        @DisplayName("preferredSleepHours가 null: 수면 평가 생략")
        void buildEnrichedHealthData_nullPreferredSleepHours() {
            // Given
            HealthData todayData = HealthData.builder()
                    .sleepDurationMinutes(420)
                    .build();

            when(healthLogRepository.findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(
                    eq(TEST_USER_ID), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(Collections.emptyList());

            // When
            EnrichedHealthData result = healthDataService.buildEnrichedHealthData(
                    todayData, TEST_USER_ID, null);

            // Then
            assertThat(result.getSleepEvaluation()).isEmpty();
            assertThat(result.getPreferredSleepHours()).isNull();
        }

        private HealthLog createHealthLog(Integer steps, Integer sleepMinutes, LocalTime wakeUpTime) {
            return HealthLog.builder()
                    .userId(TEST_USER_ID)
                    .recordedDate(LocalDate.now().minusDays(1))
                    .steps(steps)
                    .sleepDurationMinutes(sleepMinutes)
                    .wakeUpTime(wakeUpTime)
                    .build();
        }
    }

}
