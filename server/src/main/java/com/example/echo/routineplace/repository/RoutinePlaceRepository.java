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

    /** 완전 철회(동의 철회 + 전체 삭제) 시 전량 파기용 */
    void deleteByUserId(Long userId);

    /** 감지 기능 끄기(일시 중지) 시 미확정 후보만 파기용 - 확정된 장소는 남긴다 */
    void deleteByUserIdAndStatus(Long userId, RoutinePlaceStatus status);
}
