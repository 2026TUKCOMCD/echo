package com.example.echo.prompt.service;

/*
 * [2024-02 최적화] DB 접근 최소화
 *    - EnrichedHealthData를 Context에서 직접 사용 (DB 재조회 X)
 *    - 프롬프트 템플릿에 @Cacheable 적용
 *
 * [2026-03 리팩토링] OpenAI 권장 방식 적용
 *    - buildConversationPrompt, buildHistory 제거
 *    - AIService에서 messages 배열로 직접 대화 히스토리 전송
 */
import com.example.echo.common.dto.VisitWeather;
import com.example.echo.common.dto.WeatherData;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.location.dto.LocationData;
import com.example.echo.location.dto.VisitedPlace;
import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.service.RecallTopicRotationService;
import com.example.echo.prompt.entity.PromptTemplate;
import com.example.echo.prompt.entity.PromptType;
import com.example.echo.prompt.repository.PromptTemplateRepository;
import com.example.echo.user.dto.UserPreferences;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 프롬프트 서비스
 *
 * 역할: 대화용 프롬프트 생성
 * - DB에서 프롬프트 템플릿 조회 (캐싱 적용)
 * - UserContext에서 필요한 데이터 추출 (DB 재조회 없음)
 * - 템플릿 변수 치환 후 최종 프롬프트 반환
 * - 일기 프롬프트(buildDiaryPrompt)는 DiaryService가 호출
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private static final String NO_VISITED_PLACES_TEXT = "오늘 방문한 장소 정보가 없습니다.";

    private final PromptTemplateRepository promptTemplateRepository;

    /**
     * 시스템 프롬프트 생성
     *
     * AI의 페르소나와 대화 규칙을 정의하는 프롬프트
     * 모든 대화의 기본이 되며, 대화 시작 시 1회 생성
     *
     * [최적화] Context에서 EnrichedHealthData 직접 사용 - DB 재조회 없음
     *
     * 템플릿 변수 (v11):
     * - 사용자 정보: {{userName}}, {{userAge}}, {{userBirthday}}
     * - 선호도: {{hobby}}, {{job}}, {{family}}, {{preferredTopics}}, {{preferredSleepHours}}
     * - 현재 날씨: {{weather}}, {{temperature}}
     * - 위치: {{currentCity}}, {{visitedPlacesText}}, {{todayActivityGuide}}
     * - 건강 데이터: {{steps}}, {{exerciseDistance}}, {{exerciseActivity}}, {{activityList}}
     * - 수면 상세: {{sleepDuration}}, {{sleepStartTime}}, {{wakeUpTime}}
     * - 평가 데이터: {{sleepEvaluation}}, {{stepsEvaluation}}, {{wakeTimeEvaluation}}
     * - 장기기억: {{lifeMemories}}, {{recallGuide}}
     *
     * @param context ContextService에서 전달받은 UserContext
     * @return 컴파일된 시스템 프롬프트 문자열
     * @throws IllegalStateException 활성화된 SYSTEM 템플릿이 없을 경우
     */
    public String buildSystemPrompt(UserContext context) {
        return buildSystemPrompt(context, List.of(), null);
    }

    /**
     * 시스템 프롬프트 생성 (장기기억 포함, 오늘의 회상 주제 없음)
     */
    public String buildSystemPrompt(UserContext context, List<Memory> lifeMemories) {
        return buildSystemPrompt(context, lifeMemories, null);
    }

    /**
     * 시스템 프롬프트 생성 (장기기억 + 오늘의 회상 주제 포함)
     *
     * v9부터 {{lifeMemories}} 변수로 이전 대화에서 추출된 자전적 기억을 주입한다.
     * 오늘의 방문 장소(단서)와 옛 기억을 엮는 회상 대화의 재료가 된다.
     *
     * v10부터 {{todayActivityGuide}} 변수로 오늘 활동 질문 방식을 서버가 확정해 내려준다.
     * 유효한 장소명(역지오코딩 성공)이 하나라도 있으면 장소를 언급하는 질문 지시문을,
     * 없으면 장소 언급 없이 오늘 활동을 묻는 지시문을 준다. 위치 유무는 서버만 알 수 있는
     * 사실이므로 AI가 추측하지 않도록 완성된 문장으로 확정해 내려준다.
     *
     * v11부터 {{recallGuide}} 변수로 오늘의 회상 주제(날짜 기반 로테이션)를 주입한다.
     * 하루 한 주제라 세션 내내 고정이므로 시스템 프롬프트에 함께 구워 넣는다.
     *
     * @param context      ContextService에서 전달받은 UserContext
     * @param lifeMemories 저장된 장기기억 목록 (비어 있어도 됨)
     * @param recallGuide  buildRecallGuide()로 만든 오늘의 회상 주제 안내 (null 허용)
     */
    public String buildSystemPrompt(UserContext context, List<Memory> lifeMemories, String recallGuide) {
        // 1. 템플릿 조회 (캐싱 적용)
        PromptTemplate template = getActiveTemplate(PromptType.SYSTEM);

        // 2. UserContext에서 데이터 추출 (DB 접근 없음)
        UserPreferences preferences = context.getPreferences();
        WeatherData weatherData = context.getTodayWeather();

        // 3. Context에서 EnrichedHealthData 직접 사용 (DB 재조회 없음)
        EnrichedHealthData healthData = context.getEnrichedHealthData();
        Integer preferredSleepHours = preferences != null ? preferences.getPreferredSleepHours() : null;

        Map<String, Object> variables = new HashMap<>();

        // 4-1. 사용자 기본 정보
        variables.put("userName", preferences != null ? preferences.getName() : "사용자");
        variables.put("userAge", preferences != null ? preferences.getAge() : "");
        variables.put("userBirthday", preferences != null && preferences.getBirthday() != null
                ? preferences.getBirthday().toString() : "");

        // 4-2. 사용자 선호도
        variables.put("hobby", preferences != null ? preferences.getHobbies() : "");
        variables.put("job", preferences != null ? preferences.getOccupation() : "");
        variables.put("family", preferences != null ? preferences.getFamilyInfo() : "");
        variables.put("preferredTopics", preferences != null ? preferences.getPreferredTopics() : "");
        variables.put("preferredSleepHours", preferredSleepHours != null
                ? preferredSleepHours + "시간" : "");

        // 4-3. 날씨 정보
        variables.put("weather", weatherData != null ? weatherData.getDescription() : "");
        variables.put("temperature", weatherData != null && weatherData.getTemperature() != null
                ? weatherData.getTemperature() + "°C" : "");

        // 4-4. 건강 데이터 (EnrichedHealthData에서 포맷팅된 값 사용)
        variables.put("steps", healthData != null ? healthData.getStepsFormatted() : "");
        variables.put("exerciseDistance", healthData != null ? healthData.getExerciseDistanceFormatted() : "");
        variables.put("exerciseActivity", healthData != null ? healthData.getExerciseActivity() : "");
        variables.put("sleepInfo", healthData != null ? healthData.getSleepDurationFormatted() : "");
        variables.put("activityList", healthData != null ? healthData.getActivityList() : "");

        // 4-5. 수면 상세 데이터 (EnrichedHealthData에서 포맷팅된 값 사용)
        variables.put("sleepDuration", healthData != null ? healthData.getSleepDurationFormatted() : "");
        variables.put("sleepStartTime", healthData != null ? healthData.getSleepStartTimeFormatted() : "");
        variables.put("wakeUpTime", healthData != null ? healthData.getWakeUpTimeFormatted() : "");

        // 4-6. 평가 데이터 (EnrichedHealthData에서 이미 계산된 값 사용)
        variables.put("sleepEvaluation", healthData != null ? healthData.getSleepEvaluation() : "");
        variables.put("stepsEvaluation", healthData != null ? healthData.getStepsEvaluation() : "");
        variables.put("wakeTimeEvaluation", healthData != null ? healthData.getWakeTimeEvaluation() : "");

        // 4-7. 위치 정보 (방문 시점 날씨 포함)
        // 역지오코딩 실패로 placeName이 없는 장소는 "오늘 활동" 판단에서 제외한다
        // (그대로 두면 프롬프트에 "- null"이 들어가고, AI가 실패한 장소를 언급하려 든다)
        LocationData locationData = context.getLocationData();
        List<VisitedPlace> namedPlaces = extractNamedPlaces(locationData);

        // 방문 장소를 외출/집으로 분류(isHome)해 대화 재료를 나눈다.
        // - 외출: 집 좌표에서 먼 곳 → "다녀오셨네요" 회상 대상
        // - 집 낮 체류: 집인데 낮 시간대 → "집에서 어떻게 지내셨어요" (야간=취침은 제외)
        List<VisitedPlace> outings = namedPlaces.stream()
                .filter(place -> !place.isHome())
                .toList();
        List<VisitedPlace> daytimeHomeStays = namedPlaces.stream()
                .filter(VisitedPlace::isHome)
                .filter(this::isDaytimeStay)
                .toList();

        variables.put("currentCity", locationData != null && locationData.getCurrentCity() != null
                ? locationData.getCurrentCity() : "");
        variables.put("visitedPlacesText", buildVisitedPlacesText(outings, daytimeHomeStays));
        variables.put("todayActivityGuide", buildTodayActivityGuide(outings, daytimeHomeStays));

        if (namedPlaces.isEmpty()) {
            log.info("[프롬프트] 위치 데이터 없음 - locationData 존재: {}", locationData != null);
        } else {
            log.debug("[프롬프트] currentCity: {}", variables.get("currentCity"));
            log.debug("[프롬프트] visitedPlacesText:\n{}", variables.get("visitedPlacesText"));
        }

        // 4-8. 장기기억 (v9~) - 이전 대화들에서 추출·저장된 자전적 기억
        variables.put("lifeMemories", buildLifeMemoriesText(lifeMemories));

        // 4-9. 오늘의 회상 주제 (v11~) - 날짜 기반 로테이션으로 매일 다른 주제를 지정
        // 키를 반드시 넣어야 한다: compile()은 맵에 없는 키의 {{placeholder}}를 원문 그대로
        // 남기므로, 빠뜨리면 "{{recallGuide}}" 문자열이 그대로 모델에 전달된다
        variables.put("recallGuide", recallGuide != null && !recallGuide.isBlank()
                ? recallGuide
                : "오늘 지정된 회상 주제가 없습니다. [어르신의 지난 이야기]를 실마리로 삼으세요.");

        // 5. 템플릿 컴파일 (변수 치환) 후 반환
        return template.compile(variables);
    }

    /**
     * 오늘 활동 질문 방식을 서버가 확정한 지시문으로 반환 (2단계용)
     *
     * 방문 장소를 집/외출로 분류한 결과에 따라 질문 방식을 달리한다:
     * - 외출이 있으면(최우선): 외출한 곳을 언급하며 "다녀오셨네요" 질문. 집은 다녀온 곳으로 언급 금지
     * - 외출은 없고 낮에 집 체류만 있으면: "다녀오셨네요" 대신 낮에 집에서 어떻게 지냈는지 질문
     * - 둘 다 없으면: 장소를 언급하지 말고 오늘 하루를 일반적으로 질문
     */
    private String buildTodayActivityGuide(List<VisitedPlace> outings, List<VisitedPlace> daytimeHomeStays) {
        boolean hasOuting = outings != null && !outings.isEmpty();
        boolean hasDaytimeHome = daytimeHomeStays != null && !daytimeHomeStays.isEmpty();

        if (hasOuting) {
            return "오늘 외출하신 곳이 확인되었습니다. [오늘의 위치·활동]의 [외출한 곳] 중 체류 시간이 가장 긴 장소를 "
                    + "언급하며, 그곳에서 무엇을 하셨는지 여쭤보세요. 주소는 동/도로명 정도로 짧게 줄여 말하세요. "
                    + "거주지(집)는 다녀온 곳으로 언급하지 마세요. "
                    + "예: \"오늘 신길로 쪽에 다녀오셨네요. 거기서 어떤 일 보셨어요?\"";
        }
        if (hasDaytimeHome) {
            return "오늘은 외출 없이 주로 댁에서 지내신 것으로 보입니다. \"다녀오셨네요\"라고 하지 말고, "
                    + "낮 동안 집에서 어떻게 지내셨는지 여쭤보세요. "
                    + "예: \"오늘은 댁에서 편히 지내셨네요. 낮에는 어떻게 시간 보내셨어요?\"";
        }
        return "오늘 다녀오신 곳 정보가 없습니다. 장소를 절대 언급하지 말고, 오늘 하루 무엇을 하셨는지 여쭤보세요. "
                + "예: \"오늘은 어떻게 지내셨어요? 특별히 하신 일이 있으세요?\"";
    }

    /**
     * 오늘의 장기기억 회상 주제 안내 생성 (3단계용)
     *
     * RecallTopicRotationService가 날짜 기반으로 확정한 topic과 저장된 장기기억을 대조해,
     * 같은 topic의 기억이 이미 있으면 "심화 모드"(더 깊이 파고들기)를,
     * 없으면 "발굴 모드"(새로 이끌어내기)를 지시한다.
     *
     * 참고 단서를 하드코딩하지 않는 이유: 특정 삶의 궤적(시골 출신·취학·임금노동·자녀 유무 등)을
     * 전제하면 그 궤적에서 벗어난 어르신에게는 편향된 질문이 된다. 그래서 AI가 그 사람의
     * 데이터(선호도·지난 이야기)로 직접 구체화하도록 원칙만 안내한다.
     *
     * 결과는 buildSystemPrompt의 {{recallGuide}} 변수로 시스템 프롬프트에 구워진다.
     * 하루 한 주제라 세션 내내 고정이므로 턴별로 다시 만들 필요가 없다.
     *
     * @param topic     RecallTopicRotationService.currentTopic()이 반환한 오늘의 회상 주제
     * @param memories  대화 시작 시 조회한 장기기억 목록 (null/빈 목록 허용)
     * @return 시스템 프롬프트에 주입할 회상 주제 안내 문자열 (topic이 없으면 null)
     */
    public String buildRecallGuide(String topic, List<Memory> memories) {
        if (topic == null || topic.isBlank()) {
            return null;
        }

        Memory matched = memories == null ? null : memories.stream()
                .filter(memory -> topic.equals(memory.getTopic()))
                .findFirst()
                .orElse(null);

        if (matched != null) {
            return "[오늘의 회상 주제] " + topic + "\n"
                    + "[심화 모드] 아래는 어르신께서 이 주제로 전에 들려주신 기억입니다. "
                    + "이미 아는 내용으로 자연스럽게 받아, 아직 여쭙지 못한 부분(그때의 감정, 함께한 사람, 그 시절의 의미)을 더 여쭤보세요.\n"
                    + "- [" + matched.getLifePeriod() + "] " + matched.getContent();
        }

        return "[오늘의 회상 주제] " + topic + "\n"
                + "[발굴 모드] 이 주제로는 아직 들려주신 이야기가 없습니다. "
                + "특정 삶의 배경(시골 출신, 취학 여부, 직장 생활, 자녀 유무 등)을 전제하지 말고, "
                + "누구에게나 자연스러운 범용적인 질문으로 이 주제에 대한 기억을 새로 여쭤보세요.";
    }

    /**
     * 역지오코딩에 성공해 장소명이 있는 방문 장소만 추림
     *
     * placeName이 없으면(역지오코딩 실패) "오늘 활동" 판단·표시 대상에서 제외한다.
     */
    private List<VisitedPlace> extractNamedPlaces(LocationData locationData) {
        if (locationData == null || locationData.getVisitedPlaces() == null) {
            return List.of();
        }
        return locationData.getVisitedPlaces().stream()
                .filter(place -> place.getPlaceName() != null && !place.getPlaceName().isBlank())
                .toList();
    }

    /**
     * 장기기억 목록을 시스템 프롬프트용 텍스트로 변환
     *
     * 저장된 기억이 없으면 AI가 "아는 이야기"로 착각하지 않도록 명시적으로 없음을 알린다.
     */
    private String buildLifeMemoriesText(List<Memory> memories) {
        if (memories == null || memories.isEmpty()) {
            return "아직 들려주신 옛 이야기가 없습니다. 오늘 새로 여쭤보세요.";
        }

        StringBuilder sb = new StringBuilder();
        memories.forEach(memory -> {
            sb.append("- [").append(memory.getLifePeriod()).append("/").append(memory.getTopic()).append("] ")
                    .append(memory.getContent());
            if (memory.getTags() != null && !memory.getTags().isBlank()) {
                sb.append(" (태그: ").append(memory.getTags()).append(")");
            }
            sb.append("\n");
        });

        return sb.toString().trim();
    }

    /**
     * 일기 생성 프롬프트 생성
     *
     * 대화 종료 시 DiaryService가 호출
     * 하루 1개 일기를 증분 갱신하기 위해 기존 일기 내용을 함께 전달
     *
     * 템플릿 변수 (v3):
     * - {{userName}}: 사용자 이름
     * - {{todayContext}}: 오늘의 건강 데이터/날씨 요약
     * - {{existingDiary}}: 오늘 이미 생성된 일기 내용 (없으면 "(없음)")
     * - {{conversationHistory}}: 이번 세션의 대화 내용
     *
     * @param context 대화 종료 시점의 UserContext
     * @param existingDiaryContent 오늘 기존 일기 내용 (null 허용)
     * @return 컴파일된 일기 프롬프트 문자열
     * @throws IllegalStateException 활성화된 DIARY 템플릿이 없을 경우
     */
    public String buildDiaryPrompt(UserContext context, String existingDiaryContent) {
        PromptTemplate template = getActiveTemplate(PromptType.DIARY);

        UserPreferences preferences = context.getPreferences();

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", preferences != null ? preferences.getName() : "사용자");
        variables.put("todayContext", buildTodayContextText(context));
        variables.put("existingDiary", existingDiaryContent != null && !existingDiaryContent.isBlank()
                ? existingDiaryContent : "(없음)");
        variables.put("conversationHistory", buildConversationHistoryText(context.getConversationHistory()));

        return template.compile(variables);
    }

    /**
     * 장기기억 추출 프롬프트 생성
     *
     * 대화 종료 시 MemoryService가 호출
     * [기존 기억 전체 + 이번 세션 대화]를 함께 전달해 통합된 전체 목록을 재생성하게 함
     *
     * 템플릿 변수 (v2):
     * - {{userName}}: 사용자 이름
     * - {{existingMemories}}: 기존에 저장된 기억 목록 (없으면 "(없음)")
     * - {{conversationHistory}}: 이번 세션의 대화 내용
     * - {{topicVocabulary}}: topic 필드에 허용되는 어휘 목록 (RecallTopicRotationService.TOPICS와 동일해야
     *   3단계 회상 주제 로테이션의 발굴/심화 모드 매칭이 어긋나지 않음)
     *
     * @param context 대화 종료 시점의 UserContext
     * @param existingMemories 기존에 저장된 장기기억 목록 (null 허용)
     * @return 컴파일된 기억 추출 프롬프트 문자열
     * @throws IllegalStateException 활성화된 MEMORY 템플릿이 없을 경우
     */
    public String buildMemoryPrompt(UserContext context, List<Memory> existingMemories) {
        PromptTemplate template = getActiveTemplate(PromptType.MEMORY);

        UserPreferences preferences = context.getPreferences();

        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", preferences != null ? preferences.getName() : "사용자");
        variables.put("existingMemories", buildExistingMemoriesText(existingMemories));
        variables.put("conversationHistory", buildConversationHistoryText(context.getConversationHistory()));
        variables.put("topicVocabulary", String.join(", ", RecallTopicRotationService.TOPICS));

        return template.compile(variables);
    }

    /**
     * 기존 장기기억 목록을 프롬프트용 텍스트로 변환
     */
    private String buildExistingMemoriesText(List<Memory> memories) {
        if (memories == null || memories.isEmpty()) {
            return "(없음)";
        }

        StringBuilder sb = new StringBuilder();
        memories.forEach(memory -> {
            sb.append("- [").append(memory.getLifePeriod()).append("/").append(memory.getTopic()).append("] ")
                    .append(memory.getContent());
            if (memory.getTags() != null && !memory.getTags().isBlank()) {
                sb.append(" (태그: ").append(memory.getTags()).append(")");
            }
            sb.append("\n");
        });

        return sb.toString().trim();
    }

    /**
     * 오늘의 건강 데이터/날씨를 일기 프롬프트용 텍스트로 변환
     */
    private String buildTodayContextText(UserContext context) {
        EnrichedHealthData healthData = context.getEnrichedHealthData();
        WeatherData weatherData = context.getTodayWeather();

        StringBuilder sb = new StringBuilder();
        if (healthData != null) {
            if (healthData.getStepsFormatted() != null && !healthData.getStepsFormatted().isBlank()) {
                sb.append("- 걸음 수: ").append(healthData.getStepsFormatted()).append("\n");
            }
            if (healthData.getSleepDurationFormatted() != null && !healthData.getSleepDurationFormatted().isBlank()) {
                sb.append("- 수면: ").append(healthData.getSleepDurationFormatted()).append("\n");
            }
            if (healthData.getExerciseDistanceFormatted() != null && !healthData.getExerciseDistanceFormatted().isBlank()) {
                sb.append("- 운동 거리: ").append(healthData.getExerciseDistanceFormatted()).append("\n");
            }
            if (healthData.getActivityList() != null && !healthData.getActivityList().isBlank()) {
                sb.append("- 활동: ").append(healthData.getActivityList()).append("\n");
            }
        }
        if (weatherData != null && weatherData.getDescription() != null) {
            sb.append("- 오늘 날씨: ").append(weatherData.getDescription());
            if (weatherData.getTemperature() != null) {
                sb.append(", ").append(weatherData.getTemperature()).append("°C");
            }
            sb.append("\n");
        }

        String text = sb.toString().trim();
        return text.isEmpty() ? "(건강 데이터 없음)" : text;
    }

    /**
     * 대화 히스토리를 일기 프롬프트용 텍스트로 변환
     *
     * 첫 인사 턴은 userMessage가 null이므로 AI 발화만 기록
     */
    private String buildConversationHistoryText(List<com.example.echo.context.domain.ConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return "(대화 내용 없음)";
        }

        StringBuilder sb = new StringBuilder();
        history.forEach(turn -> {
            if (turn.getUserMessage() != null) {
                sb.append("어르신: ").append(turn.getUserMessage()).append("\n");
            }
            if (turn.getAiResponse() != null) {
                sb.append("AI: ").append(turn.getAiResponse()).append("\n");
            }
        });

        String text = sb.toString().trim();
        return text.isEmpty() ? "(대화 내용 없음)" : text;
    }

    /**
     * 집 낮 체류로 인정하는 시간대 (이 창과 조금이라도 겹치면 낮 체류로 본다)
     * - 이 밖(깊은 밤/이른 새벽)에만 있던 집 체류는 취침/휴식으로 보고 활동 질문에서 제외
     */
    private static final java.time.LocalTime DAY_START = java.time.LocalTime.of(8, 0);
    private static final java.time.LocalTime DAY_END = java.time.LocalTime.of(22, 0);

    /**
     * 방문 장소를 외출/집 두 섹션으로 나눠 텍스트로 변환
     *
     * - [외출한 곳]: 집이 아닌 곳. 체류 시간 내림차순. 장소명·시간·방문 시점 날씨 표시
     * - [집에서 보낸 시간]: 낮에 집에 머문 시간대. 장소명 대신 시간/체류만 표시(집 주소 노출 최소화)
     *
     * @param outings          외출 장소 (isHome=false)
     * @param daytimeHomeStays 낮 시간대 집 체류 (isHome=true & 낮)
     * @return 포맷팅된 방문 장소 텍스트 (둘 다 없으면 안내 문구)
     */
    private String buildVisitedPlacesText(List<VisitedPlace> outings, List<VisitedPlace> daytimeHomeStays) {
        boolean hasOuting = outings != null && !outings.isEmpty();
        boolean hasHome = daytimeHomeStays != null && !daytimeHomeStays.isEmpty();
        if (!hasOuting && !hasHome) {
            return NO_VISITED_PLACES_TEXT;
        }

        StringBuilder sb = new StringBuilder();

        if (hasOuting) {
            sb.append("[외출한 곳]\n");
            outings.stream()
                    .sorted((a, b) -> Integer.compare(
                            b.getStayDurationMinutes() != null ? b.getStayDurationMinutes() : 0,
                            a.getStayDurationMinutes() != null ? a.getStayDurationMinutes() : 0))
                    .forEach(place -> appendOutingLine(sb, place));
        }

        if (hasHome) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append("[집에서 보낸 시간] (낮 시간대)\n");
            daytimeHomeStays.stream()
                    .sorted((a, b) -> Integer.compare(
                            b.getStayDurationMinutes() != null ? b.getStayDurationMinutes() : 0,
                            a.getStayDurationMinutes() != null ? a.getStayDurationMinutes() : 0))
                    .forEach(place -> appendHomeStayLine(sb, place));
        }

        return sb.toString().trim();
    }

    /**
     * 외출 장소 한 줄 포맷: "- 장소명 (시간~시간, N분 체류) (날씨: ...)"
     */
    private void appendOutingLine(StringBuilder sb, VisitedPlace place) {
        sb.append(String.format("- %s", place.getPlaceName()));

        if (place.getVisitStartTime() != null && place.getVisitEndTime() != null) {
            sb.append(String.format(" (%s~%s",
                    formatTime(place.getVisitStartTime()),
                    formatTime(place.getVisitEndTime())));
            if (place.getStayDurationMinutes() != null) {
                sb.append(String.format(", %d분 체류", place.getStayDurationMinutes()));
            }
            sb.append(")");
        }

        VisitWeather weather = place.getWeather();
        if (weather != null && weather.getDescription() != null) {
            sb.append(String.format(" (날씨: %s", weather.getDescription()));
            if (weather.getTemperature() != null) {
                sb.append(String.format(", %d°C", weather.getTemperature()));
            }
            sb.append(")");
        }

        sb.append("\n");
    }

    /**
     * 집 체류 한 줄 포맷: "- 시간~시간 (N분)" (집이라 장소명은 표시하지 않음)
     */
    private void appendHomeStayLine(StringBuilder sb, VisitedPlace place) {
        if (place.getVisitStartTime() != null && place.getVisitEndTime() != null) {
            sb.append(String.format("- %s~%s",
                    formatTime(place.getVisitStartTime()),
                    formatTime(place.getVisitEndTime())));
            if (place.getStayDurationMinutes() != null) {
                sb.append(String.format(" (%d분)", place.getStayDurationMinutes()));
            }
        } else if (place.getStayDurationMinutes() != null) {
            sb.append(String.format("- 약 %d분 체류", place.getStayDurationMinutes()));
        } else {
            sb.append("- 집에서 머묾");
        }
        sb.append("\n");
    }

    /**
     * 집 체류가 낮 시간대(DAY_START~DAY_END)와 조금이라도 겹치는지 판정.
     * 시간 정보가 없으면 낮으로 간주(활동 질문 대상 유지).
     * 자정을 넘기는 체류(예: 23시~다음날 7시)는 겹침이 성립하지 않아 자연히 제외된다.
     */
    private boolean isDaytimeStay(VisitedPlace place) {
        java.time.LocalTime start = place.getVisitStartTime();
        java.time.LocalTime end = place.getVisitEndTime();
        if (start == null || end == null) {
            return true;
        }
        return start.isBefore(DAY_END) && end.isAfter(DAY_START);
    }

    /**
     * LocalTime을 "오전/오후 H시 mm분" 형식으로 포맷팅
     */
    private String formatTime(java.time.LocalTime time) {
        if (time == null) return "";
        int hour = time.getHour();
        int minute = time.getMinute();
        String period = hour < 12 ? "오전" : "오후";
        int displayHour = hour <= 12 ? hour : hour - 12;
        if (displayHour == 0) displayHour = 12;

        if (minute == 0) {
            return String.format("%s %d시", period, displayHour);
        }
        return String.format("%s %d시 %d분", period, displayHour, minute);
    }

    /**
     * 활성화된 프롬프트 템플릿 조회 (캐싱 적용)
     *
     * @param type 프롬프트 타입 (SYSTEM, CONVERSATION, DIARY)
     * @return 활성화된 프롬프트 템플릿
     * @throws IllegalStateException 활성화된 템플릿이 없을 경우
     */
    @Cacheable(value = "promptTemplates", key = "#type")
    public PromptTemplate getActiveTemplate(PromptType type) {
        return promptTemplateRepository
                .findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(type)
                .orElseThrow(() -> new IllegalStateException(
                        "활성화된 " + type + " 프롬프트 템플릿이 없습니다."));
    }

    /**
     * 프롬프트 템플릿 캐시 삭제
     *
     * 템플릿 수정/추가 시 호출하여 캐시 갱신
     */
    @CacheEvict(value = "promptTemplates", allEntries = true)
    public void evictTemplateCache() {
        // 캐시 삭제만 수행
    }

}
