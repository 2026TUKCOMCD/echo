package com.example.echo.memory.service;

import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.location.dto.LocationData;
import com.example.echo.memory.config.MemoryRecallProperties;
import com.example.echo.memory.entity.Memory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 대화 중 장기기억 검색 - 언제 검색할지(게이트), 무엇으로 검색할지(질의), 찾은 기억을 세션 블록에 쌓는 일을 맡는다.
 *
 * 대화 시작 시 오늘 외출한 곳으로 한 번, 이후 어르신 발화마다 조건부로 검색해 UserContext.recalledMemories에
 * 덧붙인다. 이 목록은 세션 동안 누적되고, AIService가 매 턴 현재 발화 바로 앞에 [어르신의 지난 이야기]로 넣는다.
 * 한 번 붙은 기억을 빼면 AI가 몇 턴 전에 자기가 꺼낸 이야기를 이어가지 못한다.
 *
 * 대화 단계를 판단하지 않는다. 검색 결과(유사도 임계값) 자체가 게이트이고, 앞단에서는 명백히 무의미한 발화
 * (첫 안부 대답, 맞장구)만 거른다 - 검색할 것을 건너뛰는 손해(기억을 놓침)가 거를 것을 검색하는 손해
 * (임베딩 1회, 임계값이 한 번 더 거름)보다 크다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRecallService {

    /** 맞장구 비교 전에 빼는 공백·문장부호·기호 ("네~", "응, 그래." 등) */
    private static final Pattern NON_WORD = Pattern.compile("[\\s\\p{P}\\p{S}]");

    private final MemoryService memoryService;
    private final MemoryRecallProperties properties;

    /**
     * 대화 시작 검색 질의 - 오늘 외출한 곳 이름 (집 제외). 외출 기록이 없으면 비어 있다
     *
     * "오늘 다녀온 곳:" 같은 머리말이나 활동명(걷기 등)은 붙이지 않는다 - 실측에서 엉뚱한 기억의 점수만 올렸다.
     */
    public Optional<String> greetingQuery(UserContext context) {
        LocationData location = context.getLocationData();
        if (location == null || location.getVisitedPlaces() == null) {
            return Optional.empty();
        }
        String places = location.getVisitedPlaces().stream()
                .filter(place -> !place.isHome())
                // PromptService가 장소를 부를 때와 같은 이름 (루틴 장소는 카테고리 라벨이 우선)
                .map(place -> place.getRoutineCategory() != null ? place.getRoutineCategory() : place.getPlaceName())
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        return places.isEmpty() ? Optional.empty() : Optional.of(places);
    }

    /**
     * 발화 검색 질의 - 직전 AI 발화 + 어르신 발화. 게이트에 걸리면 비어 있다
     *
     * AI 발화는 직전 것 하나만 넣는다 - 더 넓히면 AI의 긴 발화가 질의를 지배한다.
     * 짧은 대답("부산")은 AI 질문("고향이 어디세요?")과 함께여야 뜻이 산다.
     * 대화 원문이 로그에 남지 않도록 건너뛴 이유만 기록한다.
     *
     * @param context 이번 발화를 아직 기록하지 않은 컨텍스트
     */
    public Optional<String> turnQuery(UserContext context, String userMessage) {
        List<ConversationTurn> history = context.getConversationHistory();
        // 첫 AI 인사는 userMessage가 null이라 세지 않는다
        long previousUserTurns = history.stream().filter(turn -> turn.getUserMessage() != null).count();
        if (previousUserTurns < properties.getSkipUserTurns()) {
            log.info("장기기억 검색 건너뜀 - 첫 발화 - userId: {}", context.getUserId());
            return Optional.empty();
        }
        String normalized = NON_WORD.matcher(userMessage).replaceAll("");
        if (normalized.isEmpty() || properties.getBackchannels().contains(normalized)) {
            log.info("장기기억 검색 건너뜀 - 맞장구 - userId: {}", context.getUserId());
            return Optional.empty();
        }
        String previousAi = history.isEmpty() ? null : history.get(history.size() - 1).getAiResponse();
        return Optional.of(previousAi == null ? userMessage : previousAi + "\n" + userMessage);
    }

    /**
     * 검색해서 새로 찾은 기억을 세션 블록에 덧붙인다. 이미 붙은 기억은 다시 찾지 않는다
     *
     * @return 이번에 새로 붙은 기억 (검색이 실패하면 빈 목록이고, 블록에 이미 있는 기억은 그대로 남는다)
     */
    public List<Memory> recallInto(UserContext context, String query) {
        List<Memory> recalled = context.getRecalledMemories();
        Set<Long> alreadyRecalled = recalled.stream().map(Memory::getId).collect(Collectors.toSet());
        List<Memory> found = memoryService.search(
                context.getUserId(), query, alreadyRecalled, properties.getTopK(), properties.getThreshold());
        recalled.addAll(found);
        return found;
    }
}
