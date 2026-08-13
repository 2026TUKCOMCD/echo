package com.example.echo.memory.service;

import com.example.echo.ai.exception.AIException;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    private static final Long TEST_USER_ID = 1L;

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private PromptService promptService;

    @Mock
    private AIService aiService;

    @Captor
    private ArgumentCaptor<List<Memory>> savedMemoriesCaptor;

    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        // ObjectMapper는 파싱 로직 자체가 검증 대상이므로 실물 사용
        memoryService = new MemoryService(memoryRepository, promptService, aiService, new ObjectMapper());
    }

    private UserContext contextWithUserMessage() {
        UserContext context = UserContext.builder()
                .userId(TEST_USER_ID)
                .conversationHistory(new CopyOnWriteArrayList<>())
                .build();
        context.getConversationHistory().add(ConversationTurn.builder()
                .aiResponse("안녕하세요!")
                .timestamp(LocalDateTime.now())
                .build());
        context.getConversationHistory().add(ConversationTurn.builder()
                .userMessage("젊었을 때 부산에서 배를 탔지")
                .aiResponse("어부로 일하셨군요!")
                .timestamp(LocalDateTime.now())
                .build());
        return context;
    }

    private Memory memory(String content) {
        return Memory.builder()
                .userId(TEST_USER_ID)
                .lifePeriod("청년기")
                .topic("직업")
                .content(content)
                .tags("부산")
                .build();
    }

    @Test
    @DisplayName("유효한 JSON 응답을 받으면 기존 기억을 지우고 새 목록으로 교체한다")
    void extractAndSaveMemories_replacesWithExtractedList() {
        // given
        UserContext context = contextWithUserMessage();
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of());
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(eq("기억 프롬프트"), any())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "직업", "content": "30대에 부산에서 어부로 일했다", "tags": ["부산", "어부"]}]
                """);

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verify(memoryRepository).deleteByUserId(TEST_USER_ID);
        verify(memoryRepository).saveAll(savedMemoriesCaptor.capture());

        List<Memory> saved = savedMemoriesCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(saved.get(0).getLifePeriod()).isEqualTo("청년기");
        assertThat(saved.get(0).getTopic()).isEqualTo("직업");
        assertThat(saved.get(0).getContent()).isEqualTo("30대에 부산에서 어부로 일했다");
        assertThat(saved.get(0).getTags()).isEqualTo("부산,어부");
    }

    @Test
    @DisplayName("코드 블록으로 감싸진 응답도 파싱한다")
    void extractAndSaveMemories_parsesCodeFencedResponse() {
        // given
        UserContext context = contextWithUserMessage();
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of());
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(anyString(), any())).thenReturn("""
                다음은 추출된 기억입니다.
                ```json
                [{"lifePeriod": "유년기", "topic": "장소", "content": "시골 외갓집에서 여름을 보냈다", "tags": ["외갓집"]}]
                ```
                """);

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verify(memoryRepository).saveAll(savedMemoriesCaptor.capture());
        assertThat(savedMemoriesCaptor.getValue()).hasSize(1);
        assertThat(savedMemoriesCaptor.getValue().get(0).getContent()).isEqualTo("시골 외갓집에서 여름을 보냈다");
    }

    @Test
    @DisplayName("JSON 파싱에 실패하면 기존 기억을 그대로 유지한다")
    void extractAndSaveMemories_keepsExistingWhenParsingFails() {
        // given
        UserContext context = contextWithUserMessage();
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID))
                .thenReturn(List.of(memory("기존 기억")));
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(anyString(), any())).thenReturn("죄송합니다, 추출할 수 없습니다.");

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verify(memoryRepository, never()).deleteByUserId(anyLong());
        verify(memoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("빈 배열이 오면 유실 방지를 위해 교체하지 않는다")
    void extractAndSaveMemories_keepsExistingWhenEmptyArray() {
        // given
        UserContext context = contextWithUserMessage();
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID))
                .thenReturn(List.of(memory("기존 기억")));
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(anyString(), any())).thenReturn("[]");

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verify(memoryRepository, never()).deleteByUserId(anyLong());
        verify(memoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AI 호출이 실패해도 예외를 던지지 않고 기존 기억을 유지한다")
    void extractAndSaveMemories_swallowsAiFailure() {
        // given
        UserContext context = contextWithUserMessage();
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(memory("기존 기억")));
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(anyString(), any()))
                .thenThrow(new AIException("AI 기억 추출 실패: 401"));

        // when & then (예외 전파 없음)
        memoryService.extractAndSaveMemories(context);

        verify(memoryRepository, never()).deleteByUserId(anyLong());
        verify(memoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("사용자 발화가 없는 세션은 AI를 호출하지 않고 스킵한다")
    void extractAndSaveMemories_skipsWhenNoUserMessage() {
        // given
        UserContext context = UserContext.builder()
                .userId(TEST_USER_ID)
                .conversationHistory(new CopyOnWriteArrayList<>())
                .build();
        context.getConversationHistory().add(ConversationTurn.builder()
                .aiResponse("안녕하세요!")
                .timestamp(LocalDateTime.now())
                .build());

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verifyNoInteractions(aiService);
        verifyNoInteractions(promptService);
        verify(memoryRepository, never()).deleteByUserId(anyLong());
        verify(memoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("상한(20개)을 넘는 응답은 앞에서부터 20개만 저장한다")
    void extractAndSaveMemories_limitsToMaxMemories() {
        // given
        UserContext context = contextWithUserMessage();
        String items = IntStream.rangeClosed(1, 25)
                .mapToObj(i -> String.format(
                        "{\"lifePeriod\": \"청년기\", \"topic\": \"사건\", \"content\": \"기억 %d\", \"tags\": [\"태그\"]}", i))
                .reduce((a, b) -> a + ", " + b)
                .orElseThrow();
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of());
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(anyString(), any())).thenReturn("[" + items + "]");

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verify(memoryRepository).saveAll(savedMemoriesCaptor.capture());
        List<Memory> saved = savedMemoriesCaptor.getValue();
        assertThat(saved).hasSize(20);
        assertThat(saved.get(0).getContent()).isEqualTo("기억 1");
        assertThat(saved.get(19).getContent()).isEqualTo("기억 20");
    }

    @Test
    @DisplayName("content가 비어있는 항목은 제외하고 저장한다")
    void extractAndSaveMemories_filtersBlankContent() {
        // given
        UserContext context = contextWithUserMessage();
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of());
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(anyString(), any())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "직업", "content": "  ", "tags": []},
                 {"lifePeriod": "중년기", "topic": "가족", "content": "손주 이름은 민준이다", "tags": ["민준"]}]
                """);

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verify(memoryRepository).saveAll(savedMemoriesCaptor.capture());
        List<Memory> saved = savedMemoriesCaptor.getValue();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getContent()).isEqualTo("손주 이름은 민준이다");
    }

    @Test
    @DisplayName("기억 개수가 절반 미만으로 급감하면 유실 의심으로 교체하지 않는다")
    void extractAndSaveMemories_guardsAgainstShrink() {
        // given: 기존 10건인데 2건만 추출됨
        UserContext context = contextWithUserMessage();
        List<Memory> existing = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> memory("기존 기억 " + i))
                .toList();
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(existing);
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(anyString(), any())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "직업", "content": "기억 A", "tags": []},
                 {"lifePeriod": "중년기", "topic": "가족", "content": "기억 B", "tags": []}]
                """);

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verify(memoryRepository, never()).deleteByUserId(anyLong());
        verify(memoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("기존 기억 목록을 프롬프트 생성에 전달한다")
    void extractAndSaveMemories_passesExistingMemoriesToPrompt() {
        // given
        UserContext context = contextWithUserMessage();
        List<Memory> existing = List.of(memory("기존 기억"));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(existing);
        when(promptService.buildMemoryPrompt(context, existing)).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(eq("기억 프롬프트"), any())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "직업", "content": "30대에 부산에서 어부로 일했다", "tags": ["부산"]}]
                """);

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verify(promptService).buildMemoryPrompt(context, existing);
    }

    @Test
    @DisplayName("저장 중 예외는 전파되어 트랜잭션 롤백을 유도한다")
    void extractAndSaveMemories_propagatesSaveFailure() {
        // given
        UserContext context = contextWithUserMessage();
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of());
        when(promptService.buildMemoryPrompt(any(), any())).thenReturn("기억 프롬프트");
        when(aiService.generateMemoryExtraction(anyString(), any())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "직업", "content": "30대에 부산에서 어부로 일했다", "tags": ["부산"]}]
                """);
        doThrow(new RuntimeException("DB 연결 끊김")).when(memoryRepository).saveAll(any());

        // when & then: 삭제만 커밋되는 것을 막기 위해 예외가 전파되어야 함
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> memoryService.extractAndSaveMemories(context))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("DB 연결 끊김");
    }

    @Test
    @DisplayName("getMemories는 저장 순서(중요도 순)대로 조회한다")
    void getMemories_returnsInStoredOrder() {
        // given
        List<Memory> stored = List.of(memory("첫 번째"), memory("두 번째"));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(stored);

        // when
        List<Memory> result = memoryService.getMemories(TEST_USER_ID);

        // then
        assertThat(result).containsExactlyElementsOf(stored);
    }
}
