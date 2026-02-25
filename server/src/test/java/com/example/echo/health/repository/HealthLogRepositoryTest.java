package com.example.echo.health.repository;

import com.example.echo.health.entity.HealthLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class HealthLogRepositoryTest {

    @Autowired
    private HealthLogRepository healthLogRepository;

    private final Long TEST_USER_ID = 1L;
    private final LocalDate TODAY = LocalDate.now();

    @BeforeEach
    void setUp() {
        healthLogRepository.deleteAll();
    }

    @Test
    @DisplayName("findByUserIdAndRecordedDate - 데이터 존재 시 조회 성공")
    void findByUserIdAndRecordedDate_found() {
        // Given
        HealthLog saved = healthLogRepository.save(createHealthLog(TEST_USER_ID, TODAY, 5000, 420));

        // When
        Optional<HealthLog> result = healthLogRepository.findByUserIdAndRecordedDate(TEST_USER_ID, TODAY);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(saved.getId());
        assertThat(result.get().getSteps()).isEqualTo(5000);
    }

    @Test
    @DisplayName("findByUserIdAndRecordedDate - 데이터 없으면 빈 Optional 반환")
    void findByUserIdAndRecordedDate_notFound() {
        // When
        Optional<HealthLog> result = healthLogRepository.findByUserIdAndRecordedDate(TEST_USER_ID, TODAY);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findFirstByUserIdOrderByRecordedDateDesc - 최신 데이터 조회")
    void findFirstByUserIdOrderByRecordedDateDesc() {
        // Given
        healthLogRepository.save(createHealthLog(TEST_USER_ID, TODAY.minusDays(2), 3000, 360));
        healthLogRepository.save(createHealthLog(TEST_USER_ID, TODAY.minusDays(1), 4000, 400));
        healthLogRepository.save(createHealthLog(TEST_USER_ID, TODAY, 5000, 420));

        // When
        Optional<HealthLog> result = healthLogRepository.findFirstByUserIdOrderByRecordedDateDesc(TEST_USER_ID);

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getRecordedDate()).isEqualTo(TODAY);
        assertThat(result.get().getSteps()).isEqualTo(5000);
    }

    @Test
    @DisplayName("findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc - 기간별 조회")
    void findByUserIdAndRecordedDateBetween() {
        // Given
        LocalDate startDate = TODAY.minusDays(6);
        for (int i = 0; i < 7; i++) {
            healthLogRepository.save(createHealthLog(TEST_USER_ID, startDate.plusDays(i), 1000 * (i + 1), 360 + i * 10));
        }

        // When
        List<HealthLog> result = healthLogRepository.findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(
                TEST_USER_ID, startDate, TODAY);

        // Then
        assertThat(result).hasSize(7);
        assertThat(result.get(0).getRecordedDate()).isEqualTo(startDate);
        assertThat(result.get(6).getRecordedDate()).isEqualTo(TODAY);
    }

    @Test
    @DisplayName("findAverageStepsByUserIdAndDateRange - 평균 걸음 수 계산")
    void findAverageStepsByUserIdAndDateRange() {
        // Given
        LocalDate startDate = TODAY.minusDays(6);
        healthLogRepository.save(createHealthLog(TEST_USER_ID, startDate, 4000, 400));
        healthLogRepository.save(createHealthLog(TEST_USER_ID, startDate.plusDays(1), 6000, 420));
        healthLogRepository.save(createHealthLog(TEST_USER_ID, startDate.plusDays(2), 5000, 440));

        // When
        Double avgSteps = healthLogRepository.findAverageStepsByUserIdAndDateRange(
                TEST_USER_ID, startDate, TODAY);

        // Then
        assertThat(avgSteps).isEqualTo(5000.0);
    }

    @Test
    @DisplayName("findAverageStepsByUserIdAndDateRange - 데이터 없으면 null 반환")
    void findAverageStepsByUserIdAndDateRange_noData() {
        // When
        Double avgSteps = healthLogRepository.findAverageStepsByUserIdAndDateRange(
                TEST_USER_ID, TODAY.minusDays(7), TODAY);

        // Then
        assertThat(avgSteps).isNull();
    }

    @Test
    @DisplayName("findAverageSleepMinutesByUserIdAndDateRange - 평균 수면 시간 계산")
    void findAverageSleepMinutesByUserIdAndDateRange() {
        // Given
        LocalDate startDate = TODAY.minusDays(6);
        healthLogRepository.save(createHealthLog(TEST_USER_ID, startDate, 4000, 360));
        healthLogRepository.save(createHealthLog(TEST_USER_ID, startDate.plusDays(1), 5000, 420));
        healthLogRepository.save(createHealthLog(TEST_USER_ID, startDate.plusDays(2), 6000, 480));

        // When
        Double avgSleepMinutes = healthLogRepository.findAverageSleepMinutesByUserIdAndDateRange(
                TEST_USER_ID, startDate, TODAY);

        // Then
        assertThat(avgSleepMinutes).isEqualTo(420.0);
    }

    @Test
    @DisplayName("existsByUserIdAndRecordedDate - 데이터 존재 확인")
    void existsByUserIdAndRecordedDate() {
        // Given
        healthLogRepository.save(createHealthLog(TEST_USER_ID, TODAY, 5000, 420));

        // When & Then
        assertThat(healthLogRepository.existsByUserIdAndRecordedDate(TEST_USER_ID, TODAY)).isTrue();
        assertThat(healthLogRepository.existsByUserIdAndRecordedDate(TEST_USER_ID, TODAY.minusDays(1))).isFalse();
        assertThat(healthLogRepository.existsByUserIdAndRecordedDate(999L, TODAY)).isFalse();
    }

    @Test
    @DisplayName("save - createdAt 자동 설정 확인")
    void save_createdAtAutoSet() {
        // Given
        HealthLog healthLog = createHealthLog(TEST_USER_ID, TODAY, 5000, 420);

        // When
        HealthLog saved = healthLogRepository.save(healthLog);

        // Then
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    private HealthLog createHealthLog(Long userId, LocalDate date, Integer steps, Integer sleepMinutes) {
        return HealthLog.builder()
                .userId(userId)
                .recordedDate(date)
                .steps(steps)
                .sleepDurationMinutes(sleepMinutes)
                .sleepStartTime(LocalTime.of(23, 0))
                .wakeUpTime(LocalTime.of(7, 0))
                .exerciseDistanceKm(2.0)
                .exerciseActivity("산책")
                .activityList("산책,걷기")
                .build();
    }
}
