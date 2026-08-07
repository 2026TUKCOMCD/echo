package com.example.echo.memory.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.repository.MemoryRepository;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * MemoryService의 실제 DB 저장 경로 검증
 *
 * 단위 테스트(MemoryServiceTest)는 리포지토리까지 목이라 확인할 수 없는 것들을 검증한다:
 * - 전량 교체(delete → saveAll)가 같은 트랜잭션 안에서 실제로 의도한 최종 상태를 만드는지
 *   (Hibernate는 flush 시 insert를 delete보다 먼저 실행하므로, 실물 DB로 확인할 가치가 있음)
 * - 컬럼 길이 제약(content 500자)에 truncate가 실제로 걸리는지
 *
 * AI 호출만 목으로 대체하므로 OpenRouter 키 없이 실행 가능하다.
 */
@DataJpaTest
@Import(MemoryServicePersistenceTest.TestConfig.class)
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:memorydb;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class MemoryServicePersistenceTest {

    private static final Long TEST_USER_ID = 1L;

    @TestConfiguration
    static class TestConfig {
        @Bean
        MemoryService memoryService(MemoryRepository memoryRepository,
                                    PromptService promptService,
                                    AIService aiService) {
            return new MemoryService(memoryRepository, promptService, aiService, new ObjectMapper());
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

    @BeforeEach
    void setUp() {
        memoryRepository.deleteAll();
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
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

    @Test
    @DisplayName("기억이 없던 사용자에게 추출 결과가 실제로 저장된다")
    void savesExtractedMemories() {
        // given
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "직업", "content": "30대에 부산에서 어부로 일했다", "tags": ["부산", "어부"]}]
                """);

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        List<Memory> stored = memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID);
        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getContent()).isEqualTo("30대에 부산에서 어부로 일했다");
        assertThat(stored.get(0).getTags()).isEqualTo("부산,어부");
        assertThat(stored.get(0).getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("전량 교체 시 기존 기억이 남지 않고 새 목록만 저장된다")
    void replacesExistingMemoriesEntirely() {
        // given: 기존 기억 4건
        memoryRepository.saveAll(List.of(
                existingMemory("기존 기억 1"),
                existingMemory("기존 기억 2"),
                existingMemory("기존 기억 3"),
                existingMemory("기존 기억 4")
        ));
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "직업", "content": "통합된 기억 A", "tags": ["A"]},
                 {"lifePeriod": "중년기", "topic": "가족", "content": "통합된 기억 B", "tags": ["B"]},
                 {"lifePeriod": "최근", "topic": "습관", "content": "통합된 기억 C", "tags": ["C"]}]
                """);

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then: insert가 delete보다 먼저 flush되더라도 최종 상태는 새 목록만 남아야 함
        List<Memory> stored = memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID);
        assertThat(stored).hasSize(3);
        assertThat(stored).extracting(Memory::getContent)
                .containsExactly("통합된 기억 A", "통합된 기억 B", "통합된 기억 C");
    }

    @Test
    @DisplayName("다른 사용자의 기억은 교체 대상에서 제외된다")
    void doesNotTouchOtherUsersMemories() {
        // given
        Memory otherUsersMemory = Memory.builder()
                .userId(999L)
                .lifePeriod("청년기")
                .topic("직업")
                .content("다른 사용자의 기억")
                .build();
        memoryRepository.save(otherUsersMemory);
        memoryRepository.save(existingMemory("내 기존 기억"));
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "직업", "content": "내 새 기억", "tags": []}]
                """);

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
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
        memoryRepository.save(existingMemory("보존되어야 할 기억"));
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("추출할 수 없습니다");

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        assertThat(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID))
                .extracting(Memory::getContent).containsExactly("보존되어야 할 기억");
    }

    private Memory existingMemory(String content) {
        return Memory.builder()
                .userId(TEST_USER_ID)
                .lifePeriod("청년기")
                .topic("직업")
                .content(content)
                .tags("태그")
                .build();
    }
}
