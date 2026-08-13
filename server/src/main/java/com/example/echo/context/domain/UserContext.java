package com.example.echo.context.domain;

import com.example.echo.common.dto.WeatherData;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.location.dto.LocationData;
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

    /**
     * 캐싱된 시스템 프롬프트
     * 대화 시작 시 1회 생성되어 대화 종료까지 재사용
     */
    private String systemPrompt;

    /**
     * 이번 세션에서 사용할 OpenRouter 모델
     * 대화 시작 시 ModelRotationService.pickModelForSession()으로 1회 확정되어
     * 대화 종료까지(인사·응답·일기·기억 추출 모두) 재사용된다
     */
    private String sessionModel;

    private LocalDateTime lastAccessTime;
    private boolean isActive;
}