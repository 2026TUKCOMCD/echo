package com.example.echo.memory.service;

import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.repository.MemoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryEmbeddingBackfillRunnerTest {

    private static final String MODEL_TAG = "text-embedding-3-large@3";

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private EmbeddingService embeddingService;

    private MemoryEmbeddingBackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new MemoryEmbeddingBackfillRunner(memoryRepository, embeddingService);
        when(embeddingService.modelTag()).thenReturn(MODEL_TAG);
    }

    @Test
    @DisplayName("백필할 것이 없으면 넉넉한 타임아웃으로 임베딩을 한 번 호출해 첫 대화 검색 전에 연결을 데운다")
    void warmsUpWhenNothingToBackfill() {
        when(memoryRepository.findEmbeddingTargets(MODEL_TAG)).thenReturn(List.of());
        when(embeddingService.embedAll(anyList(), eq(MemoryEmbeddingBackfillRunner.TIMEOUT)))
                .thenReturn(List.of(new float[]{1f, 0f, 0f}));

        runner.backfill();

        verify(embeddingService, times(1)).embedAll(anyList(), eq(MemoryEmbeddingBackfillRunner.TIMEOUT));
    }

    @Test
    @DisplayName("백필이 돌면 그 호출이 연결을 데우므로 워밍업 호출을 따로 하지 않는다")
    void doesNotWarmUpSeparatelyWhenBackfilling() {
        Memory target = Memory.builder().userId(1L).content("부산 영도에서 태어났다").build();
        ReflectionTestUtils.setField(target, "id", 16L);
        when(memoryRepository.findEmbeddingTargets(MODEL_TAG)).thenReturn(List.of(target));
        when(embeddingService.embedAll(List.of("부산 영도에서 태어났다"), MemoryEmbeddingBackfillRunner.TIMEOUT))
                .thenReturn(List.of(new float[]{1f, 0f, 0f}));

        runner.backfill();

        verify(embeddingService, times(1)).embedAll(anyList(), eq(MemoryEmbeddingBackfillRunner.TIMEOUT));
    }
}
