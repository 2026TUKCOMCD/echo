package com.example.echo.health.service;

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
        @DisplayName("건강 데이터 저장 성공")
        void saveHealthData() {
            // Given
            HealthData healthData = HealthData.builder()
                    .steps(6000)
                    .sleepDurationMinutes(480)
                    .exerciseDistanceKm(3.0)
                    .exerciseActivity("조깅")
                    .build();

            when(healthLogRepository.save(any(HealthLog.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // When
            HealthLog result = healthDataService.saveHealthData(TEST_USER_ID, healthData);

            // Then
            verify(healthLogRepository).save(any(HealthLog.class));
            assertThat(result.getUserId()).isEqualTo(TEST_USER_ID);
            assertThat(result.getSteps()).isEqualTo(6000);
        }
    }

    @Nested
    @DisplayName("getWeeklyAverageSteps 테스트")
    class GetWeeklyAverageStepsTest {

        @Test
        @DisplayName("7일 평균 걸음 수 반환")
        void getWeeklyAverageSteps() {
            // Given
            when(healthLogRepository.findAverageStepsByUserIdAndDateRange(
                    eq(TEST_USER_ID), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(5000.0);

            // When
            Double result = healthDataService.getWeeklyAverageSteps(TEST_USER_ID);

            // Then
            assertThat(result).isEqualTo(5000.0);
        }

        @Test
        @DisplayName("데이터 없으면 0.0 반환")
        void getWeeklyAverageSteps_noData() {
            // Given
            when(healthLogRepository.findAverageStepsByUserIdAndDateRange(
                    eq(TEST_USER_ID), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(null);

            // When
            Double result = healthDataService.getWeeklyAverageSteps(TEST_USER_ID);

            // Then
            assertThat(result).isEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("getWeeklyAverageSleepHours 테스트")
    class GetWeeklyAverageSleepHoursTest {

        @Test
        @DisplayName("7일 평균 수면 시간(시간) 반환")
        void getWeeklyAverageSleepHours() {
            // Given
            when(healthLogRepository.findAverageSleepMinutesByUserIdAndDateRange(
                    eq(TEST_USER_ID), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(420.0);

            // When
            Double result = healthDataService.getWeeklyAverageSleepHours(TEST_USER_ID);

            // Then
            assertThat(result).isEqualTo(7.0);
        }

        @Test
        @DisplayName("데이터 없으면 0.0 반환")
        void getWeeklyAverageSleepHours_noData() {
            // Given
            when(healthLogRepository.findAverageSleepMinutesByUserIdAndDateRange(
                    eq(TEST_USER_ID), any(LocalDate.class), any(LocalDate.class)))
                    .thenReturn(null);

            // When
            Double result = healthDataService.getWeeklyAverageSleepHours(TEST_USER_ID);

            // Then
            assertThat(result).isEqualTo(0.0);
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
}
