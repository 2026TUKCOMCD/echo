package com.example.echo.memory.service;

import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.repository.MemoryRepository;
import com.example.echo.memory.support.EmbeddingCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 부팅 시 장기기억 임베딩 백필
 *
 * 대상: embedding이 없거나, 현재 설정과 다른 모델·차원으로 만들어진 행
 * - 기존 기억(RAG 전환 이전 행)과 임베딩 실패로 content만 저장된 행을 채운다
 * - 모델·차원을 바꾸면 같은 경로로 전체가 다시 만들어진다
 *
 * 실패해도 부팅을 막지 않는다. 채우지 못한 행은 다음 부팅에서 다시 시도된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryEmbeddingBackfillRunner implements ApplicationRunner {

    /**
     * 배치 크기 - API 한 요청의 입력 개수 한도(2048) 안쪽에서 요청 하나가 너무 커지지 않게 나눈다
     */
    static final int BATCH_SIZE = 100;

    /**
     * 백필 타임아웃 - 대화용(500ms)을 쓰면 안 된다.
     * 부팅 직후 JVM의 첫 호출은 TLS·클라이언트 초기화로 0.7~0.9초 걸려(실측) 매 배포마다 백필이 전부 실패한다.
     * 아무도 기다리지 않는 작업이라 넉넉히 둔다.
     */
    static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final MemoryRepository memoryRepository;
    private final EmbeddingService embeddingService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            backfill();
        } catch (Exception e) {
            log.warn("장기기억 임베딩 백필 중단 - 원인: {}", e.getMessage());
        }
    }

    void backfill() {
        String modelTag = embeddingService.modelTag();
        List<Memory> targets = memoryRepository.findEmbeddingTargets(modelTag);
        if (targets.isEmpty()) {
            log.info("장기기억 임베딩 백필 - 대상 없음 (모델: {})", modelTag);
            return;
        }

        int filled = 0;
        for (int from = 0; from < targets.size(); from += BATCH_SIZE) {
            List<Memory> batch = targets.subList(from, Math.min(from + BATCH_SIZE, targets.size()));
            List<float[]> vectors = embeddingService.embedAll(batch.stream().map(Memory::getContent).toList(), TIMEOUT);
            if (vectors.isEmpty()) {
                // 넉넉한 타임아웃에도 실패했다면 API 장애·키 문제일 가능성이 높다. 남은 배치까지 시도하며 부팅을 늦추지 않는다
                break;
            }
            for (int i = 0; i < batch.size(); i++) {
                memoryRepository.updateEmbedding(batch.get(i).getId(), EmbeddingCodec.encode(vectors.get(i)), modelTag);
            }
            filled += batch.size();
        }

        if (filled == targets.size()) {
            log.info("장기기억 임베딩 백필 완료 - {}건 (모델: {})", filled, modelTag);
        } else {
            log.warn("장기기억 임베딩 백필 일부 실패 - {}/{}건 완료, 나머지는 다음 부팅에 재시도 (모델: {})",
                    filled, targets.size(), modelTag);
        }
    }
}
