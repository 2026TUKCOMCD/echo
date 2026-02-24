package com.example.echo.health.repository;

import com.example.echo.health.entity.HealthLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {

    /**
     * 특정 사용자의 특정 날짜 건강 데이터 조회
     */
    Optional<HealthLog> findByUserIdAndRecordedDate(Long userId, LocalDate recordedDate);

    /**
     * 특정 사용자의 가장 최근 건강 데이터 조회
     */
    Optional<HealthLog> findFirstByUserIdOrderByRecordedDateDesc(Long userId);

    /**
     * 특정 사용자의 기간별 건강 데이터 조회 (날짜 오름차순)
     */
    List<HealthLog> findByUserIdAndRecordedDateBetweenOrderByRecordedDateAsc(
            Long userId, LocalDate startDate, LocalDate endDate);

    /**
     * 특정 사용자의 기간별 평균 걸음 수 계산
     */
    @Query("SELECT AVG(h.steps) FROM HealthLog h " +
            "WHERE h.userId = :userId " +
            "AND h.recordedDate BETWEEN :startDate AND :endDate " +
            "AND h.steps IS NOT NULL")
    Double findAverageStepsByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 특정 사용자의 기간별 평균 수면 시간(분) 계산
     */
    @Query("SELECT AVG(h.sleepDurationMinutes) FROM HealthLog h " +
            "WHERE h.userId = :userId " +
            "AND h.recordedDate BETWEEN :startDate AND :endDate " +
            "AND h.sleepDurationMinutes IS NOT NULL")
    Double findAverageSleepMinutesByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    /**
     * 특정 사용자의 특정 날짜에 데이터 존재 여부 확인
     */
    boolean existsByUserIdAndRecordedDate(Long userId, LocalDate recordedDate);

    /**
     * 특정 사용자의 기간별 기상 시간 목록 조회 (평균 계산용)
     */
    @Query("SELECT h.wakeUpTime FROM HealthLog h " +
            "WHERE h.userId = :userId " +
            "AND h.recordedDate BETWEEN :startDate AND :endDate " +
            "AND h.wakeUpTime IS NOT NULL")
    List<java.time.LocalTime> findWakeUpTimesByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
