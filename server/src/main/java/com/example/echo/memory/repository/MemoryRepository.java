package com.example.echo.memory.repository;

import com.example.echo.memory.entity.Memory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * 백필 대상 조회 - 임베딩이 없거나 다른 모델·차원으로 만들어진 행
     */
    @Query("SELECT m FROM Memory m WHERE m.embedding IS NULL OR m.embeddingModel IS NULL OR m.embeddingModel <> :model ORDER BY m.id")
    List<Memory> findEmbeddingTargets(@Param("model") String embeddingModel);

    /**
     * 임베딩만 갱신 - 벌크 UPDATE라 @PreUpdate가 돌지 않아 updated_at(내용 변경 시각)이 바뀌지 않는다
     */
    @Transactional
    @Modifying
    @Query("UPDATE Memory m SET m.embedding = :embedding, m.embeddingModel = :model WHERE m.id = :id")
    int updateEmbedding(@Param("id") Long id,
                        @Param("embedding") byte[] embedding,
                        @Param("model") String embeddingModel);
}
