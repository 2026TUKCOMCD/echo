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
     * 템플릿 변수 (v10):
     * - 사용자 정보: {{userName}}, {{userAge}}, {{userBirthday}}
     * - 선호도: {{hobby}}, {{job}}, {{family}}, {{preferredTopics}}, {{preferredSleepHours}}
     * - 현재 날씨: {{weather}}, {{temperature}}
     * - 위치: {{currentCity}}, {{visitedPlacesText}}, {{todayActivityGuide}}
     * - 건강 데이터: {{steps}}, {{exerciseDistance}}, {{exerciseActivity}}, {{activityList}}
     * - 수면 상세: {{sleepDuration}}, {{sleepStartTime}}, {{wakeUpTime}}
     * - 평가 데이터: {{sleepEvaluation}}, {{stepsEvaluation}}, {{wakeTimeEvaluation}}
     * - 장기기억: {{lifeMemories}}
     *
     * @param context ContextService에서 전달받은 UserContext
     * @return 컴파일된 시스템 프롬프트 문자열
     * @throws IllegalStateException 활성화된 SYSTEM 템플릿이 없을 경우
     */
    public String buildSystemPrompt(UserContext context) {
        return buildSystemPrompt(context, List.of());
    }

    /**
     * 시스템 프롬프트 생성 (장기기억 포함)
     *
     * v9부터 {{lifeMemories}} 변수로 이전 대화에서 추출된 자전적 기억을 주입한다.
     * 오늘의 방문 장소(단서)와 옛 기억을 엮는 회상 대화의 재료가 된다.
     *
     * v10부터 {{todayActivityGuide}} 변수로 오늘 활동 질문 방식을 서버가 확정해 내려준다.
     * 유효한 장소명(역지오코딩 성공)이 하나라도 있으면 장소를 언급하는 질문 지시문을,
     * 없으면 장소 언급 없이 오늘 활동을 묻는 지시문을 준다. AI가 위치 유무를 스스로
     * 판단하게 두면 지시가 지켜지지 않아(2단계·3단계 혼용) 서버가 완성된 문장으로 확정한다.
     *
     * @param context      ContextService에서 전달받은 UserContext
     * @param lifeMemories 저장된 장기기억 목록 (비어 있어도 됨)
     */
    public String buildSystemPrompt(UserContext context, List<Memory> lifeMemories) {
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

        variables.put("currentCity", locationData != null && locationData.getCurrentCity() != null
                ? locationData.getCurrentCity() : "");
        variables.put("visitedPlacesText", buildVisitedPlacesText(namedPlaces));
        variables.put("todayActivityGuide", buildTodayActivityGuide(!namedPlaces.isEmpty()));

        if (namedPlaces.isEmpty()) {
            log.info("[프롬프트] 위치 데이터 없음 - locationData 존재: {}", locationData != null);
        } else {
            log.debug("[프롬프트] currentCity: {}", variables.get("currentCity"));
            log.debug("[프롬프트] visitedPlacesText:\n{}", variables.get("visitedPlacesText"));
        }

        // 4-8. 장기기억 (v9~) - 이전 대화들에서 추출·저장된 자전적 기억
        variables.put("lifeMemories", buildLifeMemoriesText(lifeMemories));

        // 5. 템플릿 컴파일 (변수 치환) 후 반환
        return template.compile(variables);
    }

    /**
     * 오늘 활동 질문 방식을 서버가 확정한 지시문으로 반환
     *
     * hasNamedPlace가 true면 [오늘 다녀오신 곳]을 언급하며 활동을 묻게 하고,
     * false면 장소를 절대 언급하지 말고 활동만 묻게 한다. (v10, 2단계용)
     */
    private String buildTodayActivityGuide(boolean hasNamedPlace) {
        if (hasNamedPlace) {
            return "오늘 다녀오신 곳이 확인되었습니다. [오늘 다녀오신 곳] 중 체류 시간이 가장 긴 장소를 "
                    + "언급하며, 그곳에서 무엇을 하셨는지 여쭤보세요. 주소는 동/도로명 정도로 짧게 줄여 말하세요. "
                    + "예: \"오늘 신길로 쪽에 다녀오셨네요. 거기서 어떤 일 보셨어요?\"";
        }
        return "오늘 다녀오신 곳 정보가 없습니다. 장소를 절대 언급하지 말고, 오늘 하루 무엇을 하셨는지 여쭤보세요. "
                + "예: \"오늘은 어떻게 지내셨어요? 특별히 하신 일이 있으세요?\"";
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
     * 템플릿 변수 (v1):
     * - {{userName}}: 사용자 이름
     * - {{existingMemories}}: 기존에 저장된 기억 목록 (없으면 "(없음)")
     * - {{conversationHistory}}: 이번 세션의 대화 내용
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
     * 방문 장소 목록을 텍스트로 변환
     *
     * 체류 시간이 긴 장소부터 정렬하여 대화 주제 우선순위 결정
     * 각 장소의 방문 시점 날씨 정보도 함께 표시
     *
     * @param places 방문 장소 목록
     * @return 포맷팅된 방문 장소 텍스트 (체류 시간 내림차순 정렬)
     */
    private String buildVisitedPlacesText(List<VisitedPlace> places) {
        if (places == null || places.isEmpty()) {
            return NO_VISITED_PLACES_TEXT;
        }

        StringBuilder sb = new StringBuilder();
        places.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getStayDurationMinutes() != null ? b.getStayDurationMinutes() : 0,
                        a.getStayDurationMinutes() != null ? a.getStayDurationMinutes() : 0))
                .forEach(place -> {
                    sb.append(String.format("- %s", place.getPlaceName()));

                    // 방문 시간 및 체류 시간 추가
                    if (place.getVisitStartTime() != null && place.getVisitEndTime() != null) {
                        sb.append(String.format(" (%s~%s",
                                formatTime(place.getVisitStartTime()),
                                formatTime(place.getVisitEndTime())));
                        if (place.getStayDurationMinutes() != null) {
                            sb.append(String.format(", %d분 체류", place.getStayDurationMinutes()));
                        }
                        sb.append(")");
                    }

                    // 방문 시점 날씨 정보 추가
                    VisitWeather weather = place.getWeather();
                    if (weather != null && weather.getDescription() != null) {
                        sb.append(String.format(" (날씨: %s", weather.getDescription()));
                        if (weather.getTemperature() != null) {
                            sb.append(String.format(", %d°C", weather.getTemperature()));
                        }
                        sb.append(")");
                    }

                    sb.append("\n");
                });

        return sb.toString().trim();
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
