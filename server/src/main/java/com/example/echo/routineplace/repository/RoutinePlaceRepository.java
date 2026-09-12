package com.example.echo.routineplace.repository;

import com.example.echo.routineplace.entity.RoutinePlace;
import com.example.echo.routineplace.entity.RoutinePlaceStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoutinePlaceRepository extends JpaRepository<RoutinePlace, Long> {

    List<RoutinePlace> findByUserId(Long userId);

    List<RoutinePlace> findByUserIdAndStatus(Long userId, RoutinePlaceStatus status);

    Optional<RoutinePlace> findByIdAndUserId(Long id, Long userId);

    /** 동의 철회 시 전량 파기용 */
    void deleteByUserId(Long userId);
}
