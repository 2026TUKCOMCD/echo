package com.example.echo.context.service;

import com.example.echo.common.client.WeatherClient;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.location.dto.LocationData;
import com.example.echo.location.dto.RawLocationData;
import com.example.echo.location.service.LocationService;
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
    private final LocationService locationService;

    /**
     * 컨텍스트 초기화 (위치 데이터 포함)
     *
     * HealthConnect 데이터 캐싱 패턴과 동일하게:
     * 1. 사용자 선호도 조회
     * 2. EnrichedHealthData 생성
     * 3. RawLocationData → LocationService → LocationData 변환
     * 4. UserContext 생성 후 contextStore에 저장 (세션 동안 재사용)
     *
     * @param userId          사용자 ID
     * @param healthData      오늘 건강 데이터 (null이면 DB에서 조회)
     * @param rawLocationData 앱에서 전송된 원시 위치 데이터 (null 허용)
     * @return 초기화된 UserContext
     */
    public UserContext initializeContext(Long userId, HealthData healthData, RawLocationData rawLocationData) {
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

        // 3. EnrichedHealthData 생성
        EnrichedHealthData enrichedHealthData = healthDataService.buildEnrichedHealthData(
                effectiveHealthData, userId, preferredSleepHours);

        // 4. 위치 데이터 변환: RawLocationData → LocationService → LocationData
        //    변환 결과는 contextStore에 저장되어 세션 동안 재사용 (API 재호출 없음)
        LocationData locationData = locationService.enrichLocationData(rawLocationData);

        // 5. 컨텍스트 생성 및 저장
        UserContext context = UserContext.builder()
                .userId(userId)
                .date(LocalDate.now())
                .conversationHistory(new ArrayList<>())
                .enrichedHealthData(enrichedHealthData)
                .preferences(preferences)
                .todayWeather(weatherClient.getCurrentWeather(null, null))
                .locationData(locationData)
                .lastAccessTime(LocalDateTime.now())
                .isActive(true)
                .build();

        contextStore.put(userId, context);
        log.info("위치 데이터 컨텍스트 저장 완료 - userId: {}, currentCity: {}, 방문장소 수: {}",
                userId,
                locationData != null ? locationData.getCurrentCity() : "null",
                locationData != null && locationData.getVisitedPlaces() != null
                        ? locationData.getVisitedPlaces().size() : 0);
        log.info("컨텍스트 초기화 완료 - userId: {}", userId);
        return context;
    }

    /**
     * 컨텍스트 초기화 (위치 데이터 없이)
     */
    public UserContext initializeContext(Long userId, HealthData healthData) {
        return initializeContext(userId, healthData, null);
    }

    /**
     * 컨텍스트 초기화 (건강 데이터, 위치 데이터 없이)
     */
    public UserContext initializeContext(Long userId) {
        return initializeContext(userId, null, null);
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

        UserContext removed = contextStore.remove(userId);
        if (removed != null) {
            log.info("컨텍스트 정리 완료 - userId: {}, 총 대화 턴: {}",
                    userId, removed.getConversationHistory().size());
        } else {
            log.warn("컨텍스트 정리 실패 - 이미 제거됨 또는 존재하지 않음 - userId: {}", userId);
        }
    }
}
