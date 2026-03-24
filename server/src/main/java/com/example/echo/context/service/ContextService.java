package com.example.echo.context.service;

import com.example.echo.common.client.WeatherClient;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextService {

    private final ConcurrentHashMap<Long, UserContext> contextStore = new ConcurrentHashMap<>();

    private final UserService userService;
    private final HealthDataService healthDataService;
    private final WeatherClient weatherClient;

    /**
     * 컨텍스트 초기화
     *
     * 세션 관리만 담당 (건강 데이터 저장은 ConversationService에서 처리)
     *
     * 1. 사용자 선호도 조회 (DB 읽기 1회)
     * 2. EnrichedHealthData 생성 (7일치 배치 조회 1회 + 서버 평균 계산)
     * 3. 컨텍스트 생성 및 저장
     *
     * @param userId 사용자 ID
     * @param healthData 오늘 건강 데이터 (null이면 DB에서 조회)
     * @return 초기화된 UserContext
     */
    public UserContext initializeContext(Long userId, HealthData healthData) {
        log.info("컨텍스트 초기화 시작 - userId: {}", userId);

        // 1. 사용자 선호도 조회
        UserPreferences preferences = userService.getPreferences(userId);
        Integer preferredSleepHours = preferences != null ? preferences.getPreferredSleepHours() : null;

        // 2. healthData가 null이면 DB에서 조회
        HealthData effectiveHealthData = healthData;
        if (effectiveHealthData == null) {
            log.debug("healthData가 null이므로 DB에서 조회 - userId: {}", userId);
            effectiveHealthData = healthDataService.getTodayHealthData(userId);
        }

        // 3. EnrichedHealthData 생성 (7일치 배치 조회 + 서버 평균 계산)
        EnrichedHealthData enrichedHealthData = healthDataService.buildEnrichedHealthData(
                effectiveHealthData, userId, preferredSleepHours);

        // 4. 컨텍스트 생성
        UserContext context = UserContext.builder()
                .userId(userId)
                .date(LocalDate.now())
                .conversationHistory(new ArrayList<>())
                .enrichedHealthData(enrichedHealthData)
                .preferences(preferences)
                .todayWeather(weatherClient.getCurrentWeather())
                .lastAccessTime(LocalDateTime.now())
                .isActive(true)
                .build();

        contextStore.put(userId, context);
        log.info("컨텍스트 초기화 완료 - userId: {}", userId);
        return context;
    }

    /**
     * 컨텍스트 초기화 (건강 데이터 없이)
     *
     * DB에서 오늘 건강 데이터를 조회하여 사용
     *
     * @param userId 사용자 ID
     * @return 초기화된 UserContext
     */
    public UserContext initializeContext(Long userId) {
        return initializeContext(userId, null);
    }

    public UserContext getContext(Long userId) {
        UserContext context = contextStore.get(userId);
        if (context == null) {
            throw new IllegalStateException("Context not found for userId: " + userId);
        }
        context.setLastAccessTime(LocalDateTime.now());
        return context;
    }

    public void addConversationTurn(Long userId, String userMessage, String aiResponse) {
        UserContext context = getContext(userId);

        ConversationTurn turn = ConversationTurn.builder()
                .userMessage(userMessage)
                .aiResponse(aiResponse)
                .timestamp(LocalDateTime.now())
                .build();

        context.getConversationHistory().add(turn);
    }

    public void finalizeContext(Long userId) {
        log.info("컨텍스트 정리 시작 - userId: {}", userId);

        UserContext removed = contextStore.remove(userId); //removed 변수 추가
        if (removed != null) {
            log.info("컨텍스트 정리 완료 - userId: {}, 총 대화 턴: {}",
                    userId, removed.getConversationHistory().size());
        } else {
            log.warn("컨텍스트 정리 실패 - 이미 제거됨 또는 존재하지 않음 - userId: {}", userId);
        }
    }
}
