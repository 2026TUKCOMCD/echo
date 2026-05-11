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
 *
 * 일기 프롬프트는 DiaryService에서 담당 (단일 책임 원칙)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptTemplateRepository promptTemplateRepository;

    /**
     * 시스템 프롬프트 생성
     *
     * AI의 페르소나와 대화 규칙을 정의하는 프롬프트
     * 모든 대화의 기본이 되며, 대화 시작 시 1회 생성
     *
     * [최적화] Context에서 EnrichedHealthData 직접 사용 - DB 재조회 없음
     *
     * 템플릿 변수 (v7):
     * - 사용자 정보: {{userName}}, {{userAge}}, {{userBirthday}}
     * - 선호도: {{hobby}}, {{job}}, {{family}}, {{preferredTopics}}, {{preferredSleepHours}}
     * - 현재 날씨: {{weather}}, {{temperature}}
     * - 위치: {{currentCity}}, {{visitedPlacesText}}
     * - 건강 데이터: {{steps}}, {{exerciseDistance}}, {{exerciseActivity}}, {{activityList}}
     * - 수면 상세: {{sleepDuration}}, {{sleepStartTime}}, {{wakeUpTime}}
     * - 평가 데이터: {{sleepEvaluation}}, {{stepsEvaluation}}, {{wakeTimeEvaluation}}
     *
     * @param context ContextService에서 전달받은 UserContext
     * @return 컴파일된 시스템 프롬프트 문자열
     * @throws IllegalStateException 활성화된 SYSTEM 템플릿이 없을 경우
     */
    public String buildSystemPrompt(UserContext context) {
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
        LocationData locationData = context.getLocationData();
        if (locationData != null) {
            variables.put("currentCity", locationData.getCurrentCity() != null
                    ? locationData.getCurrentCity() : "");
            String visitedPlacesText = buildVisitedPlacesText(locationData.getVisitedPlaces());
            variables.put("visitedPlacesText", visitedPlacesText);

            // 프롬프트에 들어가는 위치 정보 로그
            log.info("[프롬프트] currentCity: {}", variables.get("currentCity"));
            log.debug("[프롬프트] visitedPlacesText:\n{}", visitedPlacesText);
        } else {
            variables.put("currentCity", "");
            variables.put("visitedPlacesText", "오늘 방문한 장소 정보가 없습니다.");
            log.info("[프롬프트] 위치 데이터 없음");
        }

        // 5. 템플릿 컴파일 (변수 치환) 후 반환
        return template.compile(variables);
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
            return "오늘 방문한 장소가 없습니다.";
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
