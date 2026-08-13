package com.example.echo.memory.service;

import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.ai.service.AIService;
import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.repository.MemoryRepository;
import com.example.echo.prompt.service.PromptService;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 장기기억 서비스
 *
 * 대화 종료 시 대화 원문에서 영구 유효한 자전적 기억을 추출해 저장한다.
 * - 대화 원문은 finalizeContext()에서 삭제되므로, 대화 종료 시점이 추출 가능한 유일한 타이밍
 * - [기존 기억 전체 + 이번 대화]를 AI에 넘겨 통합 목록을 받아 전량 교체 (병합·구체화가 여기서 일어남)
 * - 일기 생성과 독립적인 LLM 호출이며, 실패해도 대화 종료/일기에 영향을 주지 않는다
 *
 * 전량 교체 전략의 유실 방어:
 * AI가 기존 기억을 누락한 목록을 반환할 수 있으므로 빈 응답·개수 급감 시 교체를 중단하고 기존을 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryService {

    /** 시스템 프롬프트에 주입할 수 있는 현실적인 상한 */
    private static final int MAX_MEMORIES = 20;

    /** 이 개수 이상일 때만 급감 가드를 적용 (기억이 적을 땐 정상적인 병합도 큰 비율 감소로 보임) */
    private static final int SHRINK_GUARD_MIN_EXISTING = 4;
    private static final double SHRINK_GUARD_RATIO = 0.5;

    private static final int CONTENT_MAX_LENGTH = 500;
    private static final int FIELD_MAX_LENGTH = 30;
    private static final int TAGS_MAX_LENGTH = 200;

    private final MemoryRepository memoryRepository;
    private final PromptService promptService;
    private final AIService aiService;
    private final ObjectMapper objectMapper;

    /**
     * 대화 종료 시 장기기억 추출 및 갱신
     *
     * AI 호출·파싱 실패는 내부에서 삼키고 기존 기억을 유지한다(쓰기 전 단계라 무손상).
     * 반면 저장 단계의 예외는 전파시켜 트랜잭션을 롤백한다
     * (삭제만 커밋되고 저장이 실패해 기억이 통째로 사라지는 상황을 막기 위함).
     */
    @Transactional
    public void extractAndSaveMemories(UserContext context) {
        Long userId = context.getUserId();

        // 인사만 듣고 종료한 세션은 추출할 내용이 없음 (일기 생성과 동일 기준)
        if (!hasUserMessage(context.getConversationHistory())) {
            log.info("장기기억 추출 스킵 - 사용자 발화 없음 - userId: {}", userId);
            return;
        }

        List<Memory> existing = memoryRepository.findByUserIdOrderByIdAsc(userId);

        List<MemoryItem> items;
        try {
            String prompt = promptService.buildMemoryPrompt(context, existing);
            String raw = aiService.generateMemoryExtraction(prompt, context.getSessionModel());
            items = parseMemories(raw);
        } catch (Exception e) {
            log.warn("장기기억 추출 실패 - 기존 기억 {}건 유지 - userId: {}", existing.size(), userId, e);
            return;
        }

        if (items.isEmpty()) {
            log.info("장기기억 추출 결과 없음 - 기존 기억 {}건 유지 - userId: {}", existing.size(), userId);
            return;
        }

        // AI가 기존 기억을 대량 누락한 것으로 보이면 교체하지 않음
        if (existing.size() >= SHRINK_GUARD_MIN_EXISTING
                && items.size() < existing.size() * SHRINK_GUARD_RATIO) {
            log.warn("장기기억 개수 급감 감지 - 유실 의심으로 교체 중단 - userId: {}, 기존 {}건 → 추출 {}건",
                    userId, existing.size(), items.size());
            return;
        }

        List<Memory> replacement = items.stream()
                .limit(MAX_MEMORIES)
                .map(item -> toEntity(userId, item))
                .toList();

        memoryRepository.deleteByUserId(userId);
        memoryRepository.saveAll(replacement);

        log.info("장기기억 갱신 완료 - userId: {}, {}건 → {}건", userId, existing.size(), replacement.size());
    }

    /**
     * 대화 시작 시 시스템 프롬프트 주입용 조회
     */
    @Transactional(readOnly = true)
    public List<Memory> getMemories(Long userId) {
        return memoryRepository.findByUserIdOrderByIdAsc(userId);
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
    private List<MemoryItem> parseMemories(String raw) throws Exception {
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            throw new IllegalArgumentException("JSON 배열을 찾을 수 없습니다: " + raw);
        }

        List<MemoryItem> items = objectMapper.readValue(
                raw.substring(start, end + 1), new TypeReference<List<MemoryItem>>() {});

        return items.stream()
                .filter(item -> item != null && item.content() != null && !item.content().isBlank())
                .toList();
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
     * AI가 반환하는 기억 항목 (JSON 파싱용)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record MemoryItem(String lifePeriod, String topic, String content, List<String> tags) {
    }
}
