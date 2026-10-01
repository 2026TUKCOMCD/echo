package com.example.echo.memory.service;

import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.ai.service.AIService;
import com.example.echo.memory.dto.MemoryMergeItem;
import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.repository.MemoryRepository;
import com.example.echo.memory.support.EmbeddingCodec;
import com.example.echo.memory.support.VectorMath;
import com.example.echo.prompt.service.PromptService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 장기기억 서비스
 *
 * 대화 종료 시 대화 원문에서 영구 유효한 자전적 사실을 추출해 누적한다.
 * - 대화 원문은 finalizeContext()에서 삭제되므로, 대화 종료 시점이 추출 가능한 유일한 타이밍
 * - 한 행에 사실 하나. 기존 행은 지우지 않고, 새 사실을 추가하거나 같은 사실의 기존 행을 보강한다
 * - 일기 생성과 독립적인 LLM 호출이며, 실패해도 대화 종료/일기에 영향을 주지 않는다
 *
 * 처리 순서:
 * 1. LLM #1(MEMORY)로 이번 대화의 사실 추출
 * 2. 새 사실을 한 번에 임베딩
 * 3. 새 사실마다 기존 기억과 유사도를 계산해 병합 후보를 붙임
 * 4. 후보가 붙은 사실만 모아 LLM #2(MEMORY_MERGE)로 ADD / UPDATE / NOOP 판단 (후보가 하나도 없으면 호출 생략)
 * 5. UPDATE로 문장이 바뀐 행은 다시 임베딩
 * 6. 추가·보강된 행만 저장
 *
 * 대화 중에는 search()로 질의 문장과 비슷한 기억을 찾는다 (MemoryRecallService가 호출).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    /**
     * 병합 후보 유사도 하한 (text-embedding-3-large@1024 기준 - 모델을 바꾸면 다시 잰다)
     * 합성 평가 세트 실측: 같은 사실을 다른 말로 하거나 세부를 더한 새 사실 20건이 모두 대상 기억을 1등으로 찾았고,
     * 그 최저점이 0.497이라 여유를 두고 0.45.
     * 최종 판단은 LLM이 하므로 후보를 넉넉히 넘기는 쪽이 손해가 작다(후보를 놓치면 중복 행이 생긴다).
     */
    static final double MERGE_CANDIDATE_THRESHOLD = 0.45;

    /** 검색할 때 점수를 로그로 남기는 상위 건수 - 임계값 아래도 남겨야 임계값을 조정할 근거가 된다 */
    private static final int SEARCH_SCORE_LOG_SIZE = 3;

    /** 새 사실 하나에 붙이는 병합 후보 최대 개수 */
    static final int MERGE_CANDIDATE_LIMIT = 3;

    /**
     * 임베딩 타임아웃 - 대화 종료 뒤 비동기로 돌아 아무도 기다리지 않으므로 대화용 설정값(1초)을 쓰지 않는다.
     * 대화용은 어르신이 기다리는 만큼 짧게 잡는 값이다 - 500ms이던 때 JVM 첫 호출(0.7~0.9초)에 걸려 병합 판단 없이
     * 전부 추가되고, 같은 이야기가 중복 행으로 쌓였다.
     */
    static final Duration EMBEDDING_TIMEOUT = Duration.ofSeconds(10);

    private static final int CONTENT_MAX_LENGTH = 500;
    private static final int FIELD_MAX_LENGTH = 30;
    private static final int TAGS_MAX_LENGTH = 200;

    private final MemoryRepository memoryRepository;
    private final PromptService promptService;
    private final AIService aiService;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    /** 초기화 이전에 시작된 대화의 추출 결과가 비동기로 뒤늦게 저장되어 기억이 되살아나는 것을 막는 기준 시각 */
    private final Map<Long, LocalDateTime> resetAtByUser = new ConcurrentHashMap<>();

    /**
     * 대화 종료 시 장기기억 추출 및 누적
     *
     * AI 호출·파싱·임베딩 실패는 내부에서 삼키고, 저장할 수 있는 것만 저장한다.
     * 메서드 전체를 트랜잭션으로 묶지 않는다 - 외부 호출(LLM 최대 2회 + 임베딩 최대 2회) 동안
     * DB 커넥션을 쥐지 않기 위함이다. 기존 행을 지우지 않으므로 여러 단계를 한 번에 커밋할 이유도 없다.
     */
    public void extractAndSaveMemories(UserContext context) {
        Long userId = context.getUserId();

        // 인사만 듣고 종료한 세션은 추출할 내용이 없음 (일기 생성과 동일 기준)
        if (!hasUserMessage(context.getConversationHistory())) {
            log.info("장기기억 추출 스킵 - 사용자 발화 없음 - userId: {}", userId);
            return;
        }

        List<Memory> newFacts;
        try {
            String prompt = promptService.buildMemoryPrompt(context);
            newFacts = parseJsonArray(aiService.generateMemoryExtraction(prompt), new TypeReference<List<MemoryItem>>() {})
                    .stream()
                    .filter(item -> item != null && item.content() != null && !item.content().isBlank())
                    .map(item -> toEntity(userId, item))
                    .toList();
        } catch (Exception e) {
            log.warn("장기기억 추출 실패 - 기존 기억 유지 - userId: {}", userId, e);
            return;
        }

        if (newFacts.isEmpty()) {
            log.info("장기기억 추출 결과 없음 - userId: {}", userId);
            return;
        }
        log.debug("장기기억 추출 - userId: {}, 사실: {}", userId, newFacts.stream().map(Memory::getContent).toList());

        Changes changes = resolveChanges(context, newFacts);

        // AI 호출 중에 초기화될 수 있으므로 호출 전이 아니라 쓰기 직전에 확인한다
        if (startedBeforeReset(context)) {
            log.info("장기기억 추출 결과 폐기 - 체험 데이터 초기화 이전에 시작된 대화 - userId: {}", userId);
            return;
        }

        List<Memory> toSave = new ArrayList<>(changes.added());
        toSave.addAll(changes.updated());
        if (!toSave.isEmpty()) {
            memoryRepository.saveAll(toSave);
        }

        log.info("장기기억 갱신 완료 - userId: {}, 추출 {}건 → 추가 {}건, 보강 {}건, 반영 안 함 {}건",
                userId, newFacts.size(), changes.added().size(), changes.updated().size(),
                newFacts.size() - changes.added().size() - changes.updated().size());
    }

    /**
     * 질의 문장과 비슷한 기억 검색 - 유사도가 threshold 이상인 것 중 상위 limit건 (유사도 내림차순)
     *
     * 이미 대화에 붙어 있는 기억(excludeIds)은 후보에서 빼서 자리를 차지하지 않게 한다.
     * 임베딩·DB 조회가 실패하면 빈 목록을 반환한다 - 기억 없이도 대화는 이어져야 한다.
     * 대화 원문이 로그에 남지 않도록 질의 문장은 기록하지 않고 기억 번호와 점수만 남긴다.
     * 느린 검색이 어디서 시간을 쓰는지 운영 로그로 가릴 수 있게 단계별(임베딩·DB 조회·계산) 소요 시간도 남긴다.
     */
    public List<Memory> search(Long userId, String query, Set<Long> excludeIds, int limit, double threshold) {
        long start = System.currentTimeMillis();
        Optional<EmbeddingService.QueryEmbedding> queryEmbedding = embeddingService.embed(query);
        if (queryEmbedding.isEmpty()) {
            return List.of();
        }
        long embeddedAt = System.currentTimeMillis();

        List<StoredVector> candidates;
        long loadedAt;
        List<VectorMath.Scored<StoredVector>> top;
        try {
            candidates = loadComparableVectors(userId, embeddingService.modelTag()).stream()
                    .filter(stored -> !excludeIds.contains(stored.memory().getId()))
                    .toList();
            loadedAt = System.currentTimeMillis();
            top = VectorMath.topK(queryEmbedding.get().vector(), candidates, StoredVector::vector,
                    Math.max(limit, SEARCH_SCORE_LOG_SIZE), Double.NEGATIVE_INFINITY);
        } catch (Exception e) {
            log.warn("장기기억 검색 실패 - 기억 없이 진행 - userId: {}", userId, e);
            return List.of();
        }
        long scoredAt = System.currentTimeMillis();

        List<Memory> found = top.stream()
                .filter(hit -> hit.score() >= threshold)
                .limit(limit)
                .map(hit -> hit.item().memory())
                .toList();
        Integer openaiProcessingMs = queryEmbedding.get().openaiProcessingMs();
        log.info("장기기억 검색 - userId: {}, 임베딩 {}ms(OpenAI 처리 {}ms), DB 조회 {}ms({}건), 계산 {}ms, 상위: [{}] → 붙임: {}",
                userId, embeddedAt - start, openaiProcessingMs == null ? "-" : openaiProcessingMs,
                loadedAt - embeddedAt, candidates.size(), scoredAt - loadedAt,
                describeScores(top.subList(0, Math.min(top.size(), SEARCH_SCORE_LOG_SIZE))),
                found.stream().map(memory -> "#" + memory.getId()).toList());
        return found;
    }

    /**
     * 체험 데이터 초기화 - 진행 중인 추출이 이 시각을 볼 수 있도록 삭제보다 먼저 기록한다
     */
    @Transactional
    public void deleteAllMemories(Long userId, LocalDateTime resetAt) {
        resetAtByUser.put(userId, resetAt);
        memoryRepository.deleteByUserId(userId);
    }

    /**
     * 새 사실을 기존 기억과 비교해 추가할 행과 보강할 행을 정한다 (저장은 하지 않음)
     */
    private Changes resolveChanges(UserContext context, List<Memory> newFacts) {
        Long userId = context.getUserId();

        List<float[]> vectors = embeddingService.embedAll(newFacts.stream().map(Memory::getContent).toList(), EMBEDDING_TIMEOUT);
        if (vectors.isEmpty()) {
            // 후보를 찾을 수 없어 전부 추가한다. 중복 행이 생길 수 있지만 내용을 버리지 않는 쪽을 택한다
            // (벡터는 다음 부팅 백필이 채운다)
            log.warn("장기기억 임베딩 실패 - 병합 판단 없이 {}건 추가 - userId: {}", newFacts.size(), userId);
            return new Changes(newFacts, List.of());
        }

        String modelTag = embeddingService.modelTag();
        for (int i = 0; i < newFacts.size(); i++) {
            newFacts.get(i).assignEmbedding(EmbeddingCodec.encode(vectors.get(i)), modelTag);
        }

        List<StoredVector> stored = loadComparableVectors(userId, modelTag);

        List<Memory> added = new ArrayList<>();
        List<MemoryMergeItem> mergeItems = new ArrayList<>();
        for (int i = 0; i < newFacts.size(); i++) {
            List<VectorMath.Scored<StoredVector>> hits = VectorMath.topK(
                    vectors.get(i), stored, StoredVector::vector, MERGE_CANDIDATE_LIMIT, MERGE_CANDIDATE_THRESHOLD);
            if (hits.isEmpty()) {
                added.add(newFacts.get(i));
                continue;
            }
            log.info("장기기억 병합 후보 - userId: {}, 새 사실 {} → {}", userId, mergeItems.size(), describeScores(hits));
            mergeItems.add(new MemoryMergeItem(newFacts.get(i), hits.stream().map(hit -> hit.item().memory()).toList()));
        }

        if (mergeItems.isEmpty()) {
            return new Changes(added, List.of());
        }

        Changes merged = applyMergeDecisions(context, mergeItems);
        added.addAll(merged.added());
        reembed(merged.updated(), modelTag);
        return new Changes(added, merged.updated());
    }

    /**
     * 병합 후보 검색 대상 - 현재 설정과 같은 모델·차원으로 만든 벡터만 (다른 차원이 섞이면 비교할 수 없다)
     *
     * 세션에 캐시하지 않고 매번 DB에서 읽는다. 사용자당 수백 건 수준이라 전수 계산으로 충분하다.
     */
    private List<StoredVector> loadComparableVectors(Long userId, String modelTag) {
        return memoryRepository.findByUserIdOrderByIdAsc(userId).stream()
                .filter(memory -> memory.getEmbedding() != null && modelTag.equals(memory.getEmbeddingModel()))
                .map(memory -> new StoredVector(memory, EmbeddingCodec.decode(memory.getEmbedding())))
                .toList();
    }

    /**
     * 후보가 붙은 새 사실을 LLM #2로 판단해 적용한다
     *
     * 판단을 받지 못했거나 판단이 잘못된 사실은 폐기한다. 후보가 붙었다는 것은 비슷한 기억이 이미 있다는 뜻이라,
     * 확인 없이 추가하면 중복 행이 생긴다.
     */
    private Changes applyMergeDecisions(UserContext context, List<MemoryMergeItem> items) {
        Long userId = context.getUserId();

        List<MergeDecision> decisions;
        try {
            String prompt = promptService.buildMemoryMergePrompt(context, items);
            decisions = parseJsonArray(aiService.generateMemoryMerge(prompt), new TypeReference<List<MergeDecision>>() {});
        } catch (Exception e) {
            log.warn("장기기억 병합 판단 실패 - 후보가 붙은 새 사실 {}건 폐기 - userId: {}", items.size(), userId, e);
            return new Changes(List.of(), List.of());
        }

        Map<Integer, MergeDecision> byIndex = new HashMap<>();
        decisions.stream()
                .filter(decision -> decision != null && decision.index() != null)
                .forEach(decision -> byIndex.putIfAbsent(decision.index(), decision));

        List<Memory> added = new ArrayList<>();
        List<Memory> updated = new ArrayList<>();
        Set<Long> updatedIds = new HashSet<>();
        for (int i = 0; i < items.size(); i++) {
            MemoryMergeItem item = items.get(i);
            MergeDecision decision = byIndex.get(i);
            String action = decision == null || decision.action() == null ? "" : decision.action().trim().toUpperCase();

            switch (action) {
                case "ADD" -> added.add(item.newFact());
                case "NOOP" -> {
                }
                case "UPDATE" -> {
                    Memory target = findCandidate(item, decision.targetId());
                    if (target == null || decision.content() == null || decision.content().isBlank()) {
                        log.warn("장기기억 병합 판단 무효 - 새 사실 {} 폐기 (targetId: {}) - userId: {}",
                                i, decision.targetId(), userId);
                    } else if (!updatedIds.add(target.getId())) {
                        // 나중 판단이 먼저 보강한 내용을 덮어쓰면 정보가 사라진다
                        log.warn("장기기억 중복 보강 - 새 사실 {} 폐기 (#{}은 이미 보강됨) - userId: {}",
                                i, target.getId(), userId);
                    } else {
                        target.updateContent(truncate(decision.content().trim(), CONTENT_MAX_LENGTH));
                        updated.add(target);
                    }
                }
                default -> log.warn("장기기억 병합 판단 누락 - 새 사실 {} 폐기 (action: {}) - userId: {}",
                        i, decision == null ? null : decision.action(), userId);
            }
        }
        return new Changes(added, updated);
    }

    /**
     * 모델이 준 targetId가 이 사실에 붙은 후보일 때만 인정한다 (다른 사실의 후보나 없는 id를 고치지 않게)
     */
    private Memory findCandidate(MemoryMergeItem item, Long targetId) {
        if (targetId == null) {
            return null;
        }
        return item.candidates().stream()
                .filter(candidate -> targetId.equals(candidate.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 보강으로 문장이 바뀐 행을 다시 임베딩 - 빠뜨리면 저장된 내용과 검색 기준이 조용히 어긋난다
     *
     * 실패하면 updateContent가 비워 둔 벡터 그대로 저장되어 다음 부팅 백필이 채운다.
     */
    private void reembed(List<Memory> updated, String modelTag) {
        if (updated.isEmpty()) {
            return;
        }
        List<float[]> vectors = embeddingService.embedAll(updated.stream().map(Memory::getContent).toList(), EMBEDDING_TIMEOUT);
        if (vectors.isEmpty()) {
            log.warn("보강된 장기기억 재임베딩 실패 - {}건은 벡터 없이 저장, 다음 부팅 백필이 채운다", updated.size());
            return;
        }
        for (int i = 0; i < updated.size(); i++) {
            updated.get(i).assignEmbedding(EmbeddingCodec.encode(vectors.get(i)), modelTag);
        }
    }

    private String describeScores(List<VectorMath.Scored<StoredVector>> hits) {
        return hits.stream()
                .map(hit -> "#" + hit.item().memory().getId() + String.format("(%.3f)", hit.score()))
                .collect(Collectors.joining(", "));
    }

    private boolean startedBeforeReset(UserContext context) {
        LocalDateTime resetAt = resetAtByUser.get(context.getUserId());
        if (resetAt == null) {
            return false;
        }
        LocalDateTime startedAt = context.getStartedAt();
        return startedAt == null || startedAt.isBefore(resetAt);
    }

    private boolean hasUserMessage(List<ConversationTurn> history) {
        return history != null && history.stream().anyMatch(turn -> turn.getUserMessage() != null);
    }

    /**
     * AI 응답(JSON 배열)을 파싱
     *
     * 모델이 매일 로테이션되어 출력 형식이 흔들릴 수 있으므로,
     * 첫 [ 부터 마지막 ] 까지만 잘라내어 코드 블록 표시와 앞뒤 설명문을 함께 방어한다.
     */
    private <T> List<T> parseJsonArray(String raw, TypeReference<List<T>> type) throws Exception {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("JSON 배열을 찾을 수 없습니다: " + raw);
        }
        return objectMapper.readValue(raw.substring(start, end + 1), type);
    }

    private Memory toEntity(Long userId, MemoryItem item) {
        return Memory.builder()
                .userId(userId)
                .lifePeriod(truncate(item.lifePeriod(), FIELD_MAX_LENGTH))
                .topic(truncate(item.topic(), FIELD_MAX_LENGTH))
                .content(truncate(item.content(), CONTENT_MAX_LENGTH))
                .tags(truncate(joinTags(item.tags()), TAGS_MAX_LENGTH))
                .build();
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        return String.join(",", tags.stream().filter(tag -> tag != null && !tag.isBlank()).toList());
    }

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength);
    }

    /**
     * AI가 반환하는 추출 항목 (JSON 파싱용)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record MemoryItem(String lifePeriod, String topic, String content, List<String> tags) {
    }

    /**
     * AI가 반환하는 병합 판단 (JSON 파싱용) - targetId·content는 UPDATE일 때만 쓴다
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record MergeDecision(Integer index, String action, Long targetId, String content) {
    }

    private record StoredVector(Memory memory, float[] vector) {
    }

    private record Changes(List<Memory> added, List<Memory> updated) {
    }
}
