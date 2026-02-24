package com.example.echo.prompt.service;

/*
 * [2024-01 merge] develop 브랜치 병합으로 인한 변경사항:
 *
 * 1. import 경로 변경 (develop의 DTO 구조에 맞춤)
 *    - prompt.dto.PromptContext → context.domain.UserContext
 *    - prompt.dto.ConversationTurn → context.domain.ConversationTurn
 *    - 새로 추가: health.dto.HealthData, user.dto.UserPreferences, common.dto.WeatherData
 *
 * 2. 필드 접근 방식 변경 (UserContext 구조에 맞춤)
 *    - context.getUserName() → context.getPreferences().getName()
 *    - context.getSteps() → context.getTodayHealthData().getSteps()
 *    - context.getSleepHours() → sleepDurationMinutes / 60.0 (분→시간 변환)
 *    - context.getWeather() → context.getTodayWeather().getDescription()
 *
 * 3. buildHistory() 직접 포맷팅
 *    - develop의 ConversationTurn에 toString() 없어서 직접 포맷팅
 */
import com.example.echo.common.dto.WeatherData;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.prompt.entity.PromptTemplate;
import com.example.echo.prompt.entity.PromptType;
import com.example.echo.prompt.repository.PromptTemplateRepository;
import com.example.echo.user.dto.UserPreferences;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 프롬프트 서비스
 *
 * 역할: 대화용 프롬프트 생성
 * - DB에서 프롬프트 템플릿 조회
 * - UserContext에서 필요한 데이터 추출
 * - 템플릿 변수 치환 후 최종 프롬프트 반환
 *
 * 일기 프롬프트는 DiaryService에서 담당 (단일 책임 원칙)
 */
@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptTemplateRepository promptTemplateRepository;
    private final HealthDataService healthDataService;

    /**
     * 시스템 프롬프트 생성
     *
     * AI의 페르소나와 대화 규칙을 정의하는 프롬프트
     * 모든 대화의 기본이 되며, 대화 시작 시 1회 생성
     *
     * 템플릿 변수 (v5):
     * - 사용자 정보: {{userName}}, {{userAge}}, {{userBirthday}}
     * - 선호도: {{hobby}}, {{job}}, {{family}}, {{preferredTopics}}, {{preferredSleepHours}}
     * - 날씨: {{weather}}, {{temperature}}
     * - 건강 데이터: {{steps}}, {{exerciseDistance}}, {{exerciseActivity}}, {{sleepInfo}}
     * - 수면 상세: {{sleepDuration}}, {{sleepStartTime}}, {{wakeUpTime}}, {{activityList}}
     * - 평가 데이터: {{sleepEvaluation}}, {{stepsEvaluation}}, {{wakeTimeEvaluation}}
     *
     * @param context ContextService에서 전달받은 UserContext
     * @return 컴파일된 시스템 프롬프트 문자열
     * @throws IllegalStateException 활성화된 SYSTEM 템플릿이 없을 경우
     */
    public String buildSystemPrompt(UserContext context) {
        // 1. DB에서 활성화된 SYSTEM 템플릿 조회
        PromptTemplate template = promptTemplateRepository
                .findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM)
                .orElseThrow(() -> new IllegalStateException(
                        "활성화된 SYSTEM 프롬프트 템플릿이 없습니다."));

        // 2. UserContext에서 데이터 추출
        UserPreferences preferences = context.getPreferences();
        HealthData healthData = context.getTodayHealthData();
        WeatherData weatherData = context.getTodayWeather();

        Map<String, Object> variables = new HashMap<>();

        // 2-1. 사용자 기본 정보
        variables.put("userName", preferences != null ? preferences.getName() : "사용자");
        variables.put("userAge", preferences != null ? preferences.getAge() : "");
        variables.put("userBirthday", preferences != null && preferences.getBirthday() != null
                ? preferences.getBirthday().toString() : "");

        // 2-2. 사용자 선호도
        variables.put("hobby", preferences != null ? preferences.getHobbies() : "");
        variables.put("job", preferences != null ? preferences.getOccupation() : "");
        variables.put("family", preferences != null ? preferences.getFamilyInfo() : "");
        variables.put("preferredTopics", preferences != null ? preferences.getPreferredTopics() : "");

        // 2-3. 날씨 정보
        variables.put("weather", weatherData != null ? weatherData.getDescription() : "");
        variables.put("temperature", weatherData != null && weatherData.getTemperature() != null
                ? weatherData.getTemperature() + "°C" : "");

        // 2-4. 건강 데이터
        variables.put("steps", healthData != null && healthData.getSteps() != null
                ? String.format("%,d보", healthData.getSteps()) : "");
        variables.put("exerciseDistance", healthData != null && healthData.getExerciseDistanceKm() != null
                ? String.format("%.1fkm", healthData.getExerciseDistanceKm()) : "");
        variables.put("exerciseActivity", healthData != null ? healthData.getExerciseActivity() : "");
        variables.put("sleepInfo", healthData != null && healthData.getSleepDurationMinutes() != null
                ? formatSleepInfo(healthData.getSleepDurationMinutes()) : "");

        // 2-5. 수면 상세 데이터
        variables.put("sleepDuration", healthData != null && healthData.getSleepDurationMinutes() != null
                ? formatSleepInfo(healthData.getSleepDurationMinutes()) : "");
        variables.put("sleepStartTime", healthData != null && healthData.getSleepStartTime() != null
                ? formatTime(healthData.getSleepStartTime()) : "");
        variables.put("wakeUpTime", healthData != null && healthData.getWakeUpTime() != null
                ? formatTime(healthData.getWakeUpTime()) : "");
        variables.put("activityList", healthData != null && healthData.getActivityList() != null
                ? healthData.getActivityList() : "");

        // 2-6. 사용자 선호 수면 시간
        Integer preferredSleepHours = preferences != null ? preferences.getPreferredSleepHours() : null;
        variables.put("preferredSleepHours", preferredSleepHours != null
                ? preferredSleepHours + "시간" : "");

        // 2-7. 평가 데이터 (7일 평균 대비 오늘 데이터)
        Long userId = preferences != null ? preferences.getUserId() : null;
        variables.put("sleepEvaluation", buildSleepEvaluation(healthData, preferredSleepHours));
        variables.put("stepsEvaluation", buildStepsEvaluation(healthData, userId));
        variables.put("wakeTimeEvaluation", buildWakeTimeEvaluation(healthData, userId));

        // 3. 템플릿 컴파일 (변수 치환) 후 반환
        return template.compile(variables);
    }

    /**
     * 수면 시간을 읽기 쉬운 형식으로 변환
     *
     * @param minutes 수면 시간 (분)
     * @return "X시간 Y분" 형식의 문자열
     */
    private String formatSleepInfo(Integer minutes) {
        if (minutes == null) return "";
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (mins == 0) {
            return hours + "시간";
        }
        return hours + "시간 " + mins + "분";
    }

    /**
     * 시간을 읽기 쉬운 형식으로 변환
     *
     * @param time LocalTime
     * @return "오전/오후 H시 M분" 형식의 문자열
     */
    private String formatTime(LocalTime time) {
        if (time == null) return "";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("a h시 m분");
        return time.format(formatter);
    }

    /**
     * 수면 평가 문자열 빌드
     *
     * @param healthData 건강 데이터
     * @param preferredSleepHours 선호 수면 시간 (시간)
     * @return 평가 결과 ("부족", "적당", "과도") 또는 빈 문자열
     */
    private String buildSleepEvaluation(HealthData healthData, Integer preferredSleepHours) {
        if (healthData == null || healthData.getSleepDurationMinutes() == null || preferredSleepHours == null) {
            return "";
        }
        return healthDataService.evaluateSleep(healthData.getSleepDurationMinutes(), preferredSleepHours);
    }

    /**
     * 걸음 수 평가 문자열 빌드
     *
     * @param healthData 건강 데이터
     * @param userId 사용자 ID (7일 평균 조회용)
     * @return 평가 결과 ("평소보다 많음", "평소와 비슷", "평소보다 적음") 또는 빈 문자열
     */
    private String buildStepsEvaluation(HealthData healthData, Long userId) {
        if (healthData == null || healthData.getSteps() == null || userId == null) {
            return "";
        }
        Double avgSteps = healthDataService.getWeeklyAverageSteps(userId);
        return healthDataService.evaluateSteps(healthData.getSteps(), avgSteps);
    }

    /**
     * 기상 시간 평가 문자열 빌드
     *
     * @param healthData 건강 데이터
     * @param userId 사용자 ID (7일 평균 조회용)
     * @return 평가 결과 ("평소보다 일찍", "평소와 비슷", "평소보다 늦게") 또는 빈 문자열
     */
    private String buildWakeTimeEvaluation(HealthData healthData, Long userId) {
        if (healthData == null || healthData.getWakeUpTime() == null || userId == null) {
            return "";
        }
        LocalTime avgWakeTime = healthDataService.getWeeklyAverageWakeTime(userId);
        return healthDataService.evaluateWakeTime(healthData.getWakeUpTime(), avgWakeTime);
    }

    /**
     * 대화 프롬프트 생성
     *
     * 사용자 발화에 대한 AI 응답 생성을 위한 프롬프트
     * 시스템 프롬프트 + 오늘의 컨텍스트 + 대화 히스토리 + 사용자 메시지 조합
     *
     * 템플릿 변수: {{systemPrompt}}, {{todayContext}}, {{conversationHistory}}, {{userMessage}}
     *
     * @param context ContextService에서 전달받은 UserContext
     * @param userMessage 현재 사용자 발화 (STT 변환 결과)
     * @return 컴파일된 대화 프롬프트 문자열
     * @throws IllegalStateException 활성화된 CONVERSATION 템플릿이 없을 경우
     */
    public String buildConversationPrompt(UserContext context, String userMessage) {
        // 1. DB에서 활성화된 CONVERSATION 템플릿 조회
        PromptTemplate template = promptTemplateRepository
                .findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.CONVERSATION)
                .orElseThrow(() -> new IllegalStateException(
                        "활성화된 CONVERSATION 프롬프트 템플릿이 없습니다."));

        // 2. 각 구성 요소 빌드
        String systemPrompt = buildSystemPrompt(context);
        String todayContext = buildTodayContext(context);
        String conversationHistory = buildHistory(context);

        // 3. 템플릿 변수 매핑
        Map<String, Object> variables = new HashMap<>();
        variables.put("systemPrompt", systemPrompt);
        variables.put("todayContext", todayContext);
        variables.put("conversationHistory", conversationHistory);
        variables.put("userMessage", userMessage);

        // 4. 템플릿 컴파일 (변수 치환) 후 반환
        return template.compile(variables);
    }

    /**
     * 오늘의 컨텍스트 빌드 (건강 데이터 + 날씨)
     *
     * 사용자의 오늘 건강 데이터와 날씨 정보를 자연어 문장으로 포맷팅
     * AI가 맥락을 이해하고 관련 대화를 할 수 있도록 정보 제공
     *
     * @param context UserContext
     * @return 포맷팅된 오늘의 컨텍스트 문자열
     */
    String buildTodayContext(UserContext context) {
        StringBuilder sb = new StringBuilder();

        // 사용자 이름 추출
        UserPreferences preferences = context.getPreferences();
        String userName = (preferences != null && preferences.getName() != null)
                ? preferences.getName() : "사용자";

        // 1. 건강 데이터 포맷팅
        HealthData healthData = context.getTodayHealthData();
        if (healthData != null) {
            Integer steps = healthData.getSteps();
            // sleepDurationMinutes를 시간으로 변환
            Integer sleepMinutes = healthData.getSleepDurationMinutes();
            Double sleepHours = (sleepMinutes != null) ? sleepMinutes / 60.0 : null;

            if (steps != null || sleepHours != null) {
                sb.append(String.format("오늘 %s님은 ", userName));

                if (steps != null) {
                    sb.append(String.format("%,d보 걸으셨고", steps));
                }

                if (sleepHours != null) {
                    if (steps != null) {
                        sb.append(", ");
                    }
                    sb.append(String.format("%.1f시간 주무셨습니다.", sleepHours));
                } else if (steps != null) {
                    sb.append(".");
                }
            }
        }

        // 2. 날씨 데이터 포맷팅
        WeatherData weatherData = context.getTodayWeather();
        if (weatherData != null && weatherData.getDescription() != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }

            String weatherDesc = weatherData.getDescription();
            Integer temperature = weatherData.getTemperature();

            if (temperature != null) {
                sb.append(String.format("오늘 날씨는 %s이고 기온은 %d도입니다.", weatherDesc, temperature));
            } else {
                sb.append(String.format("오늘 날씨는 %s입니다.", weatherDesc));
            }
        }

        return sb.toString();
    }

    /**
     * 대화 히스토리 빌드
     *
     * 오늘의 대화 히스토리를 턴별로 포맷팅
     * AI가 이전 대화 맥락을 유지할 수 있도록 정보 제공
     *
     * 출력 형식:
     * [턴 1]
     * 사용자: (사용자 발화)
     * AI: (AI 응답)
     *
     * [턴 2]
     * ...
     *
     * @param context UserContext
     * @return 포맷팅된 대화 히스토리 문자열 (히스토리가 없으면 빈 문자열)
     */
    String buildHistory(UserContext context) {
        List<ConversationTurn> history = context.getConversationHistory();

        // 히스토리가 없거나 비어있으면 빈 문자열 반환
        if (history == null || history.isEmpty()) {
            return "";
        }

        // 각 턴을 포맷팅하여 문자열로 조합
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            ConversationTurn turn = history.get(i);

            sb.append(String.format("[턴 %d]\n", i + 1));
            sb.append(String.format("사용자: %s\n", turn.getUserMessage()));
            sb.append(String.format("AI: %s", turn.getAiResponse()));

            // 마지막 턴이 아니면 구분선 추가
            if (i < history.size() - 1) {
                sb.append("\n\n");
            }
        }

        return sb.toString();
    }
}
