package com.example.echo.memory.repository;

import com.example.echo.memory.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MemoryRepository extends JpaRepository<Memory, Long> {

    /**
     * 사용자의 전체 장기기억 조회
     *
     * id 오름차순 = 저장 순서 = AI가 반환한 중요도 순서
     */
    List<Memory> findByUserIdOrderByIdAsc(Long userId);

    /**
     * 전량 교체용 삭제 - 반드시 트랜잭션 안에서 호출할 것
     */
    void deleteByUserId(Long userId);
}
