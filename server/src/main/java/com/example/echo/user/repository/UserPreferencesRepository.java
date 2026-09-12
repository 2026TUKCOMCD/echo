package com.example.echo.user.repository;

import com.example.echo.user.entity.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, Long> {

    Optional<UserPreferences> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** 루틴 방문 장소 기능에 동의한 사용자 목록 - RoutinePlaceDetectionService 배치 대상 조회용 */
    List<UserPreferences> findAllByRoutinePlaceConsentTrue();
}
