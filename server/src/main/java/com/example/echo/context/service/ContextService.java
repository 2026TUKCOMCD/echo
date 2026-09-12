package com.example.echo.context.service;

import com.example.echo.common.client.WeatherClient;
import com.example.echo.common.dto.WeatherData;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.location.dto.LocationData;
import com.example.echo.location.dto.RawLocationData;
import com.example.echo.location.service.LocationService;
import com.example.echo.routineplace.dto.RoutinePlaceInfo;
import com.example.echo.routineplace.service.RoutinePlaceService;
import com.example.echo.routineplace.service.VisitOccurrenceRecordingService;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContextService {

    /**
     * 컨텍스트 TTL - 앱 크래시/강제 종료로 endConversation()이 호출되지 못한 경우에도
     * 마지막 접근 후 이 기간이 지나면 스케줄러가 컨텍스트를 정리한다.
     */
    private static final Duration CONTEXT_TTL = Duration.ofDays(1);

    private final ConcurrentHashMap<Long, UserContext> contextStore = new ConcurrentHashMap<>();

    private final UserService userService;
    private final HealthDataService healthDataService;
    private final WeatherClient weatherClient;
    private final LocationService locationService;
    private final VisitOccurrenceRecordingService visitOccurrenceRecordingService;
    private final RoutinePlaceService routinePlaceService;
    private final Clock clock;

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
        //    등록된 집 좌표를 함께 넘겨 방문 장소를 집/외출로 분류(isHome)한다.
        Double homeLatitude = preferences != null ? preferences.getHomeLatitude() : null;
        Double homeLongitude = preferences != null ? preferences.getHomeLongitude() : null;

        // 4-1. 루틴 방문 장소: 동의한 사용자만 오늘 방문 이력을 기록(패턴 감지 원재료).
        //      서버가 직접 동의 여부를 확인한다(클라이언트가 보낸 값을 신뢰하지 않음).
        if (rawLocationData != null && routinePlaceService.hasConsent(userId)) {
            visitOccurrenceRecordingService.recordOccurrences(
                    userId, rawLocationData, homeLatitude, homeLongitude, LocalDate.now(clock));
        }
        // 동의를 철회하면 확정 장소가 이미 전량 삭제되므로 여기서 별도 동의 체크 없이 조회해도 안전
        List<RoutinePlaceInfo> confirmedRoutinePlaces = routinePlaceService.getConfirmedPlaceInfos(userId);

        LocationData locationData = locationService.enrichLocationData(
                rawLocationData, homeLatitude, homeLongitude, confirmedRoutinePlaces);

        // 5. 컨텍스트 생성 및 저장
        WeatherData weather = weatherClient.getCachedUserWeather(userId);
        if (weather == null) {
            Double lat = rawLocationData != null ? rawLocationData.getCurrentLatitude() : null;
            Double lon = rawLocationData != null ? rawLocationData.getCurrentLongitude() : null;
            weather = weatherClient.getCurrentWeather(lat, lon);
            log.info("[컨텍스트] per-user 캐시 미스 → 좌표 fallback fetch (userId={})", userId);
        } else {
            log.info("[컨텍스트] per-user 캐시 hit (userId={})", userId);
        }
        UserContext context = UserContext.builder()
                .userId(userId)
                .date(LocalDate.now())
                .conversationHistory(new CopyOnWriteArrayList<>())
                .enrichedHealthData(enrichedHealthData)
                .preferences(preferences)
                .todayWeather(weather)
                .locationData(locationData)
                .confirmedRoutinePlaces(confirmedRoutinePlaces)
                .lastAccessTime(LocalDateTime.now(clock))
                .isActive(true)
                .build();

        contextStore.put(userId, context);
        log.debug("[컨텍스트] 저장 완료 - userId: {}, currentCity: {}, 방문장소 수: {}",
                userId,
                locationData != null ? locationData.getCurrentCity() : "null",
                locationData != null && locationData.getVisitedPlaces() != null
                        ? locationData.getVisitedPlaces().size() : 0);
        // 방문 장소 상세 로그
        if (locationData != null && locationData.getVisitedPlaces() != null) {
            locationData.getVisitedPlaces().forEach(place ->
                log.debug("[컨텍스트] 방문장소 - placeName: {}, address: {}, 체류: {}분, 날씨: {}",
                        place.getPlaceName(), place.getAddress(), place.getStayDurationMinutes(),
                        place.getWeather() != null ? place.getWeather().getDescription() : "null"));
        }
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
        context.setLastAccessTime(LocalDateTime.now(clock));
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

    /**
     * 앱 크래시/강제 종료로 endConversation()이 호출되지 못해 남아있는 컨텍스트를 TTL 기준으로 정리한다.
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Seoul")
    public void cleanupExpiredContexts() {
        LocalDateTime threshold = LocalDateTime.now(clock).minus(CONTEXT_TTL);
        contextStore.entrySet().removeIf(entry -> {
            boolean expired = entry.getValue().getLastAccessTime().isBefore(threshold);
            if (expired) {
                log.info("[컨텍스트] TTL 만료로 정리 - userId: {}, lastAccessTime: {}",
                        entry.getKey(), entry.getValue().getLastAccessTime());
            }
            return expired;
        });
    }
}
