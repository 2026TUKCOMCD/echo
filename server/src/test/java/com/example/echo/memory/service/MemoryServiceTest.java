package com.example.echo.memory.service;

import com.example.echo.ai.exception.AIException;
import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.memory.dto.MemoryMergeItem;
import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.repository.MemoryRepository;
import com.example.echo.memory.support.EmbeddingCodec;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MemoryServiceTest {

    private static final Long TEST_USER_ID = 1L;
    private static final String MODEL_TAG = "text-embedding-3-small@3";

    /** 기존 기억 #12와 같은 방향 → 유사도 1.0, 병합 후보가 된다 */
    private static final float[] NEAR = {1f, 0f, 0f};
    /** 기존 기억과 직교 → 유사도 0, 후보가 되지 않는다 */
    private static final float[] FAR = {0f, 1f, 0f};
    private static final float[] REEMBEDDED = {0f, 0f, 1f};

    @Mock
    private MemoryRepository memoryRepository;

    @Mock
    private PromptService promptService;

    @Mock
    private AIService aiService;

    @Mock
    private EmbeddingService embeddingService;

    @Captor
    private ArgumentCaptor<List<Memory>> savedMemoriesCaptor;

    private MemoryService memoryService;

    @BeforeEach
    void setUp() {
        // ObjectMapper는 파싱 로직 자체가 검증 대상이므로 실물 사용
        memoryService = new MemoryService(memoryRepository, promptService, aiService, embeddingService, new ObjectMapper());
        lenient().when(promptService.buildMemoryPrompt(any())).thenReturn("기억 프롬프트");
        lenient().when(promptService.buildMemoryMergePrompt(any(), anyList())).thenReturn("병합 프롬프트");
        lenient().when(embeddingService.modelTag()).thenReturn(MODEL_TAG);
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
                .userMessage("어머니가 시장에서 떡장사를 하셨지")
                .aiResponse("어머니께서 떡장사를 하셨군요!")
                .timestamp(LocalDateTime.now())
                .build());
        return context;
    }

    /** 임베딩까지 저장된 기존 기억 */
    private Memory stored(long id, String content, float[] vector, String modelTag) {
        Memory memory = Memory.builder()
                .userId(TEST_USER_ID)
                .lifePeriod("유년기")
                .topic("부모형제")
                .content(content)
                .tags("어머니")
                .build();
        ReflectionTestUtils.setField(memory, "id", id);
        memory.assignEmbedding(EmbeddingCodec.encode(vector), modelTag);
        return memory;
    }

    private String facts(String... contents) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < contents.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("{\"lifePeriod\": \"유년기\", \"topic\": \"부모형제\", \"content\": \"")
                    .append(contents[i]).append("\", \"tags\": [\"어머니\"]}");
        }
        return sb.append("]").toString();
    }

    private List<Memory> savedMemories() {
        verify(memoryRepository).saveAll(savedMemoriesCaptor.capture());
        return savedMemoriesCaptor.getValue();
    }

    // ===== 추가 (후보 없음) =====

    @Test
    @DisplayName("비슷한 기존 기억이 없으면 병합 판단 없이 임베딩과 함께 추가한다")
    void addsWithEmbeddingWhenNoCandidate() {
        // given
        when(aiService.generateMemoryExtraction(eq("기억 프롬프트"))).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "일", "content": "30대에 부산에서 어부로 일했다", "tags": ["부산", "어부"]}]
                """);
        when(embeddingService.embedAll(List.of("30대에 부산에서 어부로 일했다"), MemoryService.EMBEDDING_TIMEOUT)).thenReturn(List.of(FAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID))
                .thenReturn(List.of(stored(12, "어머니가 떡장사를 하셨다", NEAR, MODEL_TAG)));

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        verify(aiService, never()).generateMemoryMerge(anyString());
        verify(memoryRepository, never()).deleteByUserId(anyLong());

        List<Memory> saved = savedMemories();
        assertThat(saved).hasSize(1);
        Memory memory = saved.get(0);
        assertThat(memory.getId()).isNull();
        assertThat(memory.getUserId()).isEqualTo(TEST_USER_ID);
        assertThat(memory.getLifePeriod()).isEqualTo("청년기");
        assertThat(memory.getTopic()).isEqualTo("일");
        assertThat(memory.getContent()).isEqualTo("30대에 부산에서 어부로 일했다");
        assertThat(memory.getTags()).isEqualTo("부산,어부");
        assertThat(EmbeddingCodec.decode(memory.getEmbedding())).containsExactly(FAR);
        assertThat(memory.getEmbeddingModel()).isEqualTo(MODEL_TAG);
    }

    @Test
    @DisplayName("코드 블록으로 감싸진 응답도 파싱한다")
    void parsesCodeFencedResponse() {
        // given
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("""
                ```json
                [{"lifePeriod": "유년기", "topic": "고향", "content": "경상도 시골에서 자랐다", "tags": ["경상도"]}]
                ```
                """);
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(FAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of());

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        assertThat(savedMemories()).extracting(Memory::getContent).containsExactly("경상도 시골에서 자랐다");
    }

    @Test
    @DisplayName("다른 모델·차원으로 만든 벡터는 병합 후보에서 제외한다")
    void excludesVectorsOfOtherModel() {
        // given: 방향은 같지만 옛 모델로 만든 벡터 (백필 전)
        when(aiService.generateMemoryExtraction(anyString())).thenReturn(facts("어머니가 시장에서 떡장사를 하셨다"));
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID))
                .thenReturn(List.of(stored(12, "어머니가 떡장사를 하셨다", NEAR, "text-embedding-3-small@1536")));

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        verify(aiService, never()).generateMemoryMerge(anyString());
        assertThat(savedMemories()).extracting(Memory::getContent).containsExactly("어머니가 시장에서 떡장사를 하셨다");
    }

    // ===== 병합 판단 =====

    @Test
    @DisplayName("UPDATE 판단이면 기존 행을 id 그대로 보강하고 다시 임베딩한다")
    void updatesCandidateInPlaceAndReembeds() {
        // given
        Memory existing = stored(12, "어머니가 떡장사를 하셨다", NEAR, MODEL_TAG);
        String enriched = "어머니가 시장에서 떡장사를 하셨는데, 새벽마다 떡을 쪄서 이고 나가셨다";
        when(aiService.generateMemoryExtraction(anyString())).thenReturn(facts("어머니가 새벽마다 떡을 쪄서 이고 나가셨다"));
        when(embeddingService.embedAll(List.of("어머니가 새벽마다 떡을 쪄서 이고 나가셨다"), MemoryService.EMBEDDING_TIMEOUT)).thenReturn(List.of(NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(existing));
        when(aiService.generateMemoryMerge(eq("병합 프롬프트"))).thenReturn("""
                [{"index": 0, "action": "UPDATE", "targetId": 12, "content": "%s"}]
                """.formatted(enriched));
        when(embeddingService.embedAll(List.of(enriched), MemoryService.EMBEDDING_TIMEOUT)).thenReturn(List.of(REEMBEDDED));

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then: 후보가 붙은 사실로 병합 판단을 요청
        verify(promptService).buildMemoryMergePrompt(any(), argThat((List<MemoryMergeItem> items) ->
                items.size() == 1 && items.get(0).candidates().equals(List.of(existing))));

        List<Memory> saved = savedMemories();
        assertThat(saved).containsExactly(existing);
        assertThat(existing.getId()).isEqualTo(12L);
        assertThat(existing.getContent()).isEqualTo(enriched);
        // 재임베딩 누락은 조용히 틀리는 버그 - 벡터가 새 문장의 것이어야 한다
        assertThat(EmbeddingCodec.decode(existing.getEmbedding())).containsExactly(REEMBEDDED);
        assertThat(existing.getEmbeddingModel()).isEqualTo(MODEL_TAG);
    }

    @Test
    @DisplayName("NOOP 판단이면 아무것도 저장하지 않는다")
    void savesNothingOnNoop() {
        // given
        Memory existing = stored(12, "어머니가 떡장사를 하셨다", NEAR, MODEL_TAG);
        when(aiService.generateMemoryExtraction(anyString())).thenReturn(facts("어머니가 떡장사를 하셨다"));
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(existing));
        when(aiService.generateMemoryMerge(anyString())).thenReturn("[{\"index\": 0, \"action\": \"NOOP\"}]");

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        verify(memoryRepository, never()).saveAll(any());
        assertThat(existing.getContent()).isEqualTo("어머니가 떡장사를 하셨다");
    }

    @Test
    @DisplayName("후보가 있어도 ADD 판단이면 새 행으로 추가하고, 판단이 빠진 사실은 폐기한다")
    void addsOnAddDecisionAndDiscardsMissingDecision() {
        // given: 두 사실 모두 후보가 붙었지만 응답에는 1번 판단만 있음
        Memory existing = stored(12, "어머니가 떡장사를 하셨다", NEAR, MODEL_TAG);
        when(aiService.generateMemoryExtraction(anyString()))
                .thenReturn(facts("어머니가 떡장사를 하셨다", "어머니가 인절미를 잘 만드셨다"));
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(NEAR, NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(existing));
        when(aiService.generateMemoryMerge(anyString())).thenReturn("[{\"index\": 1, \"action\": \"add\"}]");

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        List<Memory> saved = savedMemories();
        assertThat(saved).extracting(Memory::getContent).containsExactly("어머니가 인절미를 잘 만드셨다");
        assertThat(saved.get(0).getId()).isNull();
    }

    @Test
    @DisplayName("그 사실의 후보가 아닌 targetId로 UPDATE하면 그 사실을 폐기한다")
    void discardsUpdateWithUnknownTargetId() {
        // given
        Memory existing = stored(12, "어머니가 떡장사를 하셨다", NEAR, MODEL_TAG);
        when(aiService.generateMemoryExtraction(anyString())).thenReturn(facts("어머니가 새벽에 떡을 찌셨다"));
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(existing));
        when(aiService.generateMemoryMerge(anyString())).thenReturn("""
                [{"index": 0, "action": "UPDATE", "targetId": 99, "content": "엉뚱한 행 보강"}]
                """);

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        verify(memoryRepository, never()).saveAll(any());
        assertThat(existing.getContent()).isEqualTo("어머니가 떡장사를 하셨다");
    }

    @Test
    @DisplayName("같은 기존 기억에 UPDATE가 두 번 오면 먼저 온 것만 반영한다")
    void appliesOnlyFirstUpdateOnSameTarget() {
        // given
        Memory existing = stored(12, "어머니가 떡장사를 하셨다", NEAR, MODEL_TAG);
        when(aiService.generateMemoryExtraction(anyString()))
                .thenReturn(facts("어머니가 새벽에 떡을 찌셨다", "어머니가 시장 입구에 자리를 잡으셨다"));
        when(embeddingService.embedAll(List.of("어머니가 새벽에 떡을 찌셨다", "어머니가 시장 입구에 자리를 잡으셨다"), MemoryService.EMBEDDING_TIMEOUT))
                .thenReturn(List.of(NEAR, NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(existing));
        when(aiService.generateMemoryMerge(anyString())).thenReturn("""
                [{"index": 0, "action": "UPDATE", "targetId": 12, "content": "첫 번째 보강"},
                 {"index": 1, "action": "UPDATE", "targetId": 12, "content": "두 번째 보강"}]
                """);
        when(embeddingService.embedAll(List.of("첫 번째 보강"), MemoryService.EMBEDDING_TIMEOUT)).thenReturn(List.of(REEMBEDDED));

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        assertThat(savedMemories()).containsExactly(existing);
        assertThat(existing.getContent()).isEqualTo("첫 번째 보강");
    }

    // ===== 실패 처리 =====

    @Test
    @DisplayName("병합 판단이 실패하면 후보가 붙은 사실만 폐기하고, 후보 없던 사실은 추가한다")
    void discardsOnlyFactsWithCandidatesWhenMergeFails() {
        // given
        Memory existing = stored(12, "어머니가 떡장사를 하셨다", NEAR, MODEL_TAG);
        when(aiService.generateMemoryExtraction(anyString()))
                .thenReturn(facts("어머니가 새벽에 떡을 찌셨다", "부산에서 배를 탔다"));
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(NEAR, FAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(existing));
        when(aiService.generateMemoryMerge(anyString())).thenThrow(new AIException("AI 기억 병합 판단 실패: 500"));

        // when (예외 전파 없음)
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        assertThat(savedMemories()).extracting(Memory::getContent).containsExactly("부산에서 배를 탔다");
        assertThat(existing.getContent()).isEqualTo("어머니가 떡장사를 하셨다");
    }

    @Test
    @DisplayName("새 사실 임베딩이 실패하면 병합 판단 없이 벡터 없이 전부 추가한다 (내용을 버리지 않음)")
    void addsAllWithoutEmbeddingWhenEmbeddingFails() {
        // given
        when(aiService.generateMemoryExtraction(anyString()))
                .thenReturn(facts("어머니가 떡장사를 하셨다", "부산에서 배를 탔다"));
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of());

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        verify(memoryRepository, never()).findByUserIdOrderByIdAsc(anyLong());
        verify(aiService, never()).generateMemoryMerge(anyString());
        List<Memory> saved = savedMemories();
        assertThat(saved).extracting(Memory::getContent).containsExactly("어머니가 떡장사를 하셨다", "부산에서 배를 탔다");
        assertThat(saved).allSatisfy(memory -> {
            assertThat(memory.getEmbedding()).isNull();
            assertThat(memory.getEmbeddingModel()).isNull();
        });
    }

    @Test
    @DisplayName("보강한 문장의 재임베딩이 실패하면 옛 벡터를 지운 채 저장한다 (다음 부팅 백필이 채움)")
    void clearsStaleVectorWhenReembeddingFails() {
        // given
        Memory existing = stored(12, "어머니가 떡장사를 하셨다", NEAR, MODEL_TAG);
        when(aiService.generateMemoryExtraction(anyString())).thenReturn(facts("어머니가 새벽에 떡을 찌셨다"));
        when(embeddingService.embedAll(List.of("어머니가 새벽에 떡을 찌셨다"), MemoryService.EMBEDDING_TIMEOUT)).thenReturn(List.of(NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(existing));
        when(aiService.generateMemoryMerge(anyString())).thenReturn("""
                [{"index": 0, "action": "UPDATE", "targetId": 12, "content": "어머니가 떡장사를 하셨고 새벽에 떡을 찌셨다"}]
                """);
        when(embeddingService.embedAll(List.of("어머니가 떡장사를 하셨고 새벽에 떡을 찌셨다"), MemoryService.EMBEDDING_TIMEOUT)).thenReturn(List.of());

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        assertThat(savedMemories()).containsExactly(existing);
        assertThat(existing.getContent()).isEqualTo("어머니가 떡장사를 하셨고 새벽에 떡을 찌셨다");
        assertThat(existing.getEmbedding()).isNull();
        assertThat(existing.getEmbeddingModel()).isNull();
    }

    @Test
    @DisplayName("JSON 파싱에 실패하면 아무것도 저장하지 않는다")
    void savesNothingWhenParsingFails() {
        // given
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("죄송합니다, 추출할 수 없습니다.");

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        verifyNoInteractions(embeddingService);
        verify(memoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("빈 배열이 오면 아무것도 저장하지 않는다")
    void savesNothingWhenEmptyArray() {
        // given
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("[]");

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        verifyNoInteractions(embeddingService);
        verify(memoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("AI 호출이 실패해도 예외를 던지지 않고 아무것도 저장하지 않는다")
    void swallowsAiFailure() {
        // given
        when(aiService.generateMemoryExtraction(anyString()))
                .thenThrow(new AIException("AI 기억 추출 실패: 401"));

        // when & then (예외 전파 없음)
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        verify(memoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("content가 비어있는 항목은 제외한다")
    void filtersBlankContent() {
        // given
        when(aiService.generateMemoryExtraction(anyString())).thenReturn("""
                [{"lifePeriod": "청년기", "topic": "일", "content": "", "tags": []},
                 {"lifePeriod": "청년기", "topic": "일", "content": "부산에서 배를 탔다", "tags": []}]
                """);
        when(embeddingService.embedAll(List.of("부산에서 배를 탔다"), MemoryService.EMBEDDING_TIMEOUT)).thenReturn(List.of(FAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of());

        // when
        memoryService.extractAndSaveMemories(contextWithUserMessage());

        // then
        assertThat(savedMemories()).extracting(Memory::getContent).containsExactly("부산에서 배를 탔다");
    }

    @Test
    @DisplayName("사용자 발화가 없는 세션은 AI를 호출하지 않고 스킵한다")
    void skipsWhenNoUserMessage() {
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
        verifyNoInteractions(embeddingService);
        verify(memoryRepository, never()).saveAll(any());
    }

    // ===== 체험 데이터 초기화 =====

    @Test
    @DisplayName("체험 데이터 초기화 이전에 시작된 대화의 추출 결과는 저장하지 않는다")
    void discardsResultOfSessionStartedBeforeReset() {
        // given: 10시에 시작한 대화의 추출이 도는 사이 10시 5분에 초기화됨
        UserContext context = contextWithUserMessage();
        context.setStartedAt(LocalDateTime.of(2026, 9, 12, 10, 0));
        memoryService.deleteAllMemories(TEST_USER_ID, LocalDateTime.of(2026, 9, 12, 10, 5));
        verify(memoryRepository).deleteByUserId(TEST_USER_ID);

        when(aiService.generateMemoryExtraction(anyString())).thenReturn(facts("이전 방문객의 기억"));
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(FAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of());

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        verify(memoryRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("체험 데이터 초기화 이후에 시작된 대화는 정상적으로 기억을 저장한다")
    void savesResultOfSessionStartedAfterReset() {
        // given
        UserContext context = contextWithUserMessage();
        context.setStartedAt(LocalDateTime.of(2026, 9, 12, 10, 10));
        memoryService.deleteAllMemories(TEST_USER_ID, LocalDateTime.of(2026, 9, 12, 10, 5));

        when(aiService.generateMemoryExtraction(anyString())).thenReturn(facts("새 방문객의 기억"));
        when(embeddingService.embedAll(anyList(), eq(MemoryService.EMBEDDING_TIMEOUT))).thenReturn(List.of(FAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of());

        // when
        memoryService.extractAndSaveMemories(context);

        // then
        assertThat(savedMemories()).extracting(Memory::getContent).containsExactly("새 방문객의 기억");
    }

    // ===== 검색 (대화 중) =====

    @Test
    @DisplayName("search는 임계값 이상인 기억만 유사도 높은 순으로 최대 limit건 반환한다")
    void search_returnsTopMatchesAboveThreshold() {
        // given: 질의(NEAR)와의 유사도 #1 0.6 / #2 1.0 / #3 0.8 / #4 0
        when(embeddingService.embed("질의")).thenReturn(Optional.of(NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(
                stored(1, "조금 비슷함", new float[]{0.6f, 0.8f, 0f}, MODEL_TAG),
                stored(2, "같은 이야기", NEAR, MODEL_TAG),
                stored(3, "꽤 비슷함", new float[]{0.8f, 0.6f, 0f}, MODEL_TAG),
                stored(4, "무관한 이야기", FAR, MODEL_TAG)));

        // when
        List<Memory> result = memoryService.search(TEST_USER_ID, "질의", Set.of(), 2, 0.5);

        // then
        assertThat(result).extracting(Memory::getId).containsExactly(2L, 3L);
    }

    @Test
    @DisplayName("search는 이미 대화에 붙은 기억을 빼고 찾아, 다음으로 비슷한 기억이 그 자리를 채운다")
    void search_excludesAlreadyRecalled() {
        // given
        when(embeddingService.embed("질의")).thenReturn(Optional.of(NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(
                stored(1, "조금 비슷함", new float[]{0.6f, 0.8f, 0f}, MODEL_TAG),
                stored(2, "같은 이야기", NEAR, MODEL_TAG),
                stored(3, "꽤 비슷함", new float[]{0.8f, 0.6f, 0f}, MODEL_TAG)));

        // when
        List<Memory> result = memoryService.search(TEST_USER_ID, "질의", Set.of(2L), 2, 0.5);

        // then
        assertThat(result).extracting(Memory::getId).containsExactly(3L, 1L);
    }

    @Test
    @DisplayName("search는 다른 모델·차원으로 만든 벡터와 벡터가 없는 기억은 비교하지 않는다")
    void search_skipsIncomparableVectors() {
        // given
        Memory withoutEmbedding = Memory.builder().userId(TEST_USER_ID).content("백필 전 기억").build();
        ReflectionTestUtils.setField(withoutEmbedding, "id", 2L);
        when(embeddingService.embed("질의")).thenReturn(Optional.of(NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenReturn(List.of(
                stored(1, "옛 모델로 만든 기억", NEAR, "text-embedding-3-small@1536"),
                withoutEmbedding));

        // when
        List<Memory> result = memoryService.search(TEST_USER_ID, "질의", Set.of(), 2, 0.5);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("search는 질의 임베딩에 실패하면 DB를 조회하지 않고 빈 목록을 반환한다")
    void search_returnsEmptyWhenEmbeddingFails() {
        // given
        when(embeddingService.embed("질의")).thenReturn(Optional.empty());

        // when
        List<Memory> result = memoryService.search(TEST_USER_ID, "질의", Set.of(), 2, 0.5);

        // then
        assertThat(result).isEmpty();
        verify(memoryRepository, never()).findByUserIdOrderByIdAsc(anyLong());
    }

    @Test
    @DisplayName("search는 DB 조회에 실패해도 예외를 던지지 않고 빈 목록을 반환한다 (기억 없이 대화 진행)")
    void search_returnsEmptyWhenRepositoryFails() {
        // given
        when(embeddingService.embed("질의")).thenReturn(Optional.of(NEAR));
        when(memoryRepository.findByUserIdOrderByIdAsc(TEST_USER_ID)).thenThrow(new RuntimeException("DB 연결 끊김"));

        // when
        List<Memory> result = memoryService.search(TEST_USER_ID, "질의", Set.of(), 2, 0.5);

        // then
        assertThat(result).isEmpty();
    }
}
