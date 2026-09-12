package com.example.echo.context.domain;

import com.example.echo.common.dto.WeatherData;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.location.dto.LocationData;
import com.example.echo.routineplace.dto.RoutinePlaceInfo;
import com.example.echo.user.dto.UserPreferences;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContext {
    private Long userId;
    private LocalDate date;

    @Builder.Default
    private List<ConversationTurn> conversationHistory = new CopyOnWriteArrayList<>();

    private EnrichedHealthData enrichedHealthData;
    private UserPreferences preferences;
    private WeatherData todayWeather;
    private LocationData locationData;

    /** 사용자가 확정한 루틴 방문 장소 전체 목록 (오늘 방문 여부와 무관, 대화 배경지식용) */
    @Builder.Default
    private List<RoutinePlaceInfo> confirmedRoutinePlaces = List.of();

    /**
     * 캐싱된 시스템 프롬프트
     * 대화 시작 시 1회 생성되어 대화 종료까지 재사용
     */
    private String systemPrompt;

    /**
     * STT 결과가 비어있던(무음·너무 짧은 녹음 등) 연속 횟수.
     * 알아들을 수 있는 발화가 들어오면 0으로 리셋된다. 연속 횟수에 따라
     * 재요청 안내 문구를 단계적으로 바꾸는 데 쓰인다([이탈 발화 및 무응답 대응]과 같은 패턴).
     */
    private int consecutiveEmptySttCount;

    private LocalDateTime lastAccessTime;
    private boolean isActive;
}