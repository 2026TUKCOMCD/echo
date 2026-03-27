package com.example.echo.health.repository;

import com.example.echo.health.entity.HealthLog;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * 특정 사용자의 특정 날짜에 데이터 존재 여부 확인
     */
    boolean existsByUserIdAndRecordedDate(Long userId, LocalDate recordedDate);
}
