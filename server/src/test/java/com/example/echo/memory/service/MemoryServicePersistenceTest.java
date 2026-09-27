package com.example.echo.memory.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.repository.MemoryRepository;
import com.example.echo.memory.support.EmbeddingCodec;
import com.example.echo.prompt.service.PromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MemoryService의 실제 DB 저장 경로 검증
 *
 * 단위 테스트(MemoryServiceTest)는 리포지토리까지 목이라 확인할 수 없는 것들을 검증한다:
 * - 보강(UPDATE)이 행을 새로 만들지 않고 같은 id의 행을 고치는지, updated_at이 갱신되는지
 * - 새 사실을 추가해도 기존 행이 지워지지 않는지 (전량 교체 폐기)
 * - 컬럼 길이 제약(content 500자)에 truncate가 실제로 걸리는지
 *
 * 운영에서 extractAndSaveMemories는 트랜잭션 없이 비동기 스레드에서 돈다. 조회한 엔티티가 준영속 상태로
 * 보강되어 saveAll(merge)로 반영되는 경로를 그대로 재현하려고 테스트 트랜잭션을 끈다.
 *
 * AI·임베딩 호출만 목으로 대체하므로 API 키 없이 실행 가능하다.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(MemoryServicePersistenceTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:memorydb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class MemoryServicePersistenceTest {

    private static final Long TEST_USER_ID = 1L;
    private static final String MODEL_TAG = "text-embedding-3-small@3";
    private static final float[] NEAR = {1f, 0f, 0f};
    private static final float[] FAR = {0f, 1f, 0f};
    private static final float[] REEMBEDDED = {0f, 0f, 1f};

    @TestConfiguration
    static class TestConfig {
        @Bean
        MemoryService memoryService(MemoryRepository memoryRepository,
                                    PromptService promptService,
                                    AIService aiService,
                                    EmbeddingService embeddingService) {
            return new MemoryService(memoryRepository, promptService, aiService, embeddingService, new ObjectMapper());
        }
    }

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private MemoryRepository memoryRepository;

    @MockitoBean
    private PromptService promptService;

    @MockitoBean
    private AIService aiService;

    @MockitoBean
    private EmbeddingService embeddingService;

    @BeforeEach
    void setUp() {
        memoryRepository.deleteAll();
        when(promptService.buildMemoryPrompt(any())).thenReturn("기억 프롬프트");
        when(promptService.buildMemoryMergePrompt(any(), anyList())).thenReturn("병합 프롬프트");
        when(embeddingService.modelTag()).thenReturn(MODEL_TAG);
    }

    private UserContext contextWithUserMessage() {
        UserContext context = UserContext.builder()
                .userId(TEST_USER_ID)
                .conversationHistory(new CopyOnWriteArrayList<>())
                .build();
        context.getConversationHistory().add(ConversationTurn.builder()
                .userMessage("젊었을 때 부산에서 배를 탔지")
                .aiResponse("어부로 일하셨군요!")
                .timestamp(LocalDateTime.now())
                .build());
        return context;
    }

    private Memory existingMemory(Long userId, String content, float[] vector) {
        Memory memory = Memory.builder()
                .userId(userId)
                .lifePeriod("청년기")
                .topic("일")
                .content(content)
                .tags("태그")
                .build();
        memory.assignEmbedding(EmbeddingCodec.encode(vector), MODEL_TAG);
        // 시각 비교를 위해 DB 정밀도로 저장된 값을 다시 읽어 돌려준다
        return memoryRepository.findById(memoryRepository.save(memory).getId()).orElseThrow();
    }

    @Test
    @DisplayName("기억이 없던 사용자에게 추출 결과가 임베딩과 함께 저장된다")
    void savesExtractedMemoriesWithEmbedding() {
        // given
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "일", "content": "30대에 부산에서 어부로 일했다", "tags": ["부산", "어부"]}]
                """);
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(FAR));

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        List<Memory> stored = memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getContent()).isEqualTo("30대에 부산에서 어부로 일했다");
        assertThat(stored.get(0).getTags()).isEqualTo("부산,어부");
        assertThat(stored.get(0).getCreatedAt()).isNotNull();
        assertThat(EmbeddingCodec.decode(stored.get(0).getEmbedding())).containsExactly(FAR);
        assertThat(stored.get(0).getEmbeddingModel()).isEqualTo(MODEL_TAG);
    }

    @Test
    @DisplayName("보강(UPDATE)은 같은 id의 행을 고치고 updated_at과 임베딩을 갱신한다")
    void updatesExistingRowInPlace() {
        // given
        Memory existing = existingMemory(TEST_USER_ID, "30대에 부산에서 어부로 일했다", NEAR);
        String enriched = "30대에 부산에서 어부로 일했고, 새벽마다 동료들과 배를 몰고 나갔다";
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "일", "content": "새벽마다 동료들과 배를 몰고 나갔다", "tags": ["배"]}]
                """);
        when(embeddingService.embedAll(List.of("새벽마다 동료들과 배를 몰고 나갔다"), MemoryService.EMBEDDING_TIMEOUT)).thenReturn(List.of(NEAR));
        when(aiService.generateMemoryMerge(anyString())).thenReturn("""
                [{"index": 0, "action": "UPDATE", "targetId": %d, "content": "%s"}]
                """.formatted(existing.getId(), enriched));
        when(embeddingService.embedAll(List.of(enriched), MemoryService.EMBEDDING_TIMEOUT)).thenReturn(List.of(REEMBEDDED));

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then: 새 행 없이 기존 행이 바뀜
        List<Memory> stored = memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID);
        assertThat(stored).hasSize(1);
        Memory updated = stored.get(0);
        assertThat(updated.getId()).isEqualTo(existing.getId());
        assertThat(updated.getContent()).isEqualTo(enriched);
        assertThat(updated.getTags()).isEqualTo("태그");
        assertThat(EmbeddingCodec.decode(updated.getEmbedding())).containsExactly(REEMBEDDED);
        assertThat(updated.getCreatedAt()).isEqualTo(existing.getCreatedAt());
        assertThat(updated.getUpdatedAt()).isAfter(existing.getUpdatedAt());
    }

    @Test
    @DisplayName("새 사실을 추가해도 기존 행은 지워지거나 바뀌지 않는다")
    void keepsExistingRowsWhenAdding() {
        // given
        Memory existing = existingMemory(TEST_USER_ID, "어머니가 떡장사를 하셨다", NEAR);
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "일", "content": "30대에 부산에서 어부로 일했다", "tags": []}]
                """);
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(FAR));

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        List<Memory> stored = memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID);
        assertThat(stored).extracting(Memory::getContent)
                .containsExactly("어머니가 떡장사를 하셨다", "30대에 부산에서 어부로 일했다");
        assertThat(stored.get(0).getId()).isEqualTo(existing.getId());
        assertThat(stored.get(0).getUpdatedAt()).isEqualTo(existing.getUpdatedAt());
    }

    @Test
    @DisplayName("다른 사용자의 기억은 병합 후보가 되지 않고 바뀌지도 않는다")
    void doesNotTouchOtherUsersMemories() {
        // given: 다른 사용자에게 같은 방향의 벡터가 있음
        existingMemory(999L, "다른 사용자의 기억", NEAR);
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "일", "content": "내 새 기억", "tags": []}]
                """);
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(NEAR));

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        verify(aiService, never()).generateMemoryMerge(anyString());
        assertThat(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID))
                .extracting(Memory::getContent).containsExactly("내 새 기억");
        assertThat(memoryRepository.findByUserIdOrderByIdAsc(999L))
                .extracting(Memory::getContent).containsExactly("다른 사용자의 기억");
    }

    @Test
    @DisplayName("500자를 넘는 content도 truncate되어 컬럼 제약을 위반하지 않는다")
    void truncatesOverlongContent() {
        // given
        String longContent = "가".repeat(700);
        when(aiService.generateMemoryExtraction(anyString())).thenReturn(
                "[{\"lifePeriod\": \"청년기\", \"topic\": \"사건\", \"content\": \"" + longContent + "\", \"tags\": []}]");
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(FAR));

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        List<Memory> stored = memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getContent()).hasSize(500);
    }

    @Test
    @DisplayName("추출에 실패하면 저장된 기억이 그대로 남는다")
    void keepsStoredMemoriesWhenExtractionFails() {
        // given
        existingMemory(TEST_USER_ID, "보존되어야 할 기억", NEAR);
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("추출할 수 없습니다");

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        assertThat(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID))
                .extracting(Memory::getContent).containsExactly("보존되어야 할 기억");
    }
}
