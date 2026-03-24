package com.example.echo.context.service;

import com.example.echo.common.client.WeatherClient;
import com.example.echo.common.dto.WeatherData;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContextService 테스트")
class ContextServiceTest {

    @InjectMocks
    private ContextService contextService;

    @Mock
    private UserService userService;

    @Mock
    private HealthDataService healthDataService;

    @Mock
    private WeatherClient weatherClient;

    private Long userId;
    private UserPreferences mockPreferences;
    private HealthData mockHealthData;
    private EnrichedHealthData mockEnrichedHealthData;
    private WeatherData mockWeatherData;

    @BeforeEach
    void setUp() {
        userId = 1L;

        mockPreferences = UserPreferences.builder()
                .userId(userId)
                .name("홍길동")
                .age(70)
                .location("서울시 강남구")
                .voiceSettings(VoiceSettings.builder().build())
                .preferredSleepHours(7)
                .build();

        mockHealthData = HealthData.builder()
                .steps(4200)
                .sleepDurationMinutes(390)
                .exerciseDistanceKm(1.8)
                .exerciseActivity("아침 산책")
                .build();

        mockEnrichedHealthData = EnrichedHealthData.builder()
                .steps(4200)
                .sleepDurationMinutes(390)
                .exerciseDistanceKm(1.8)
                .exerciseActivity("아침 산책")
                .stepsFormatted("4,200보")
                .sleepDurationFormatted("6시간 30분")
                .build();

        mockWeatherData = WeatherData.builder()
                .description("맑음")
                .temperature(15)
                .build();
    }

    @Nested
    @DisplayName("initializeContext 메서드")
    class InitializeContext {

        @Test
        @DisplayName("성공: 새로운 UserContext를 생성하고 저장한다 (healthData 없이)")
        void success_createsAndStoresContext_withoutHealthData() {
            // given
            given(userService.getPreferences(userId)).willReturn(mockPreferences);
            given(healthDataService.getTodayHealthData(userId)).willReturn(mockHealthData);
            given(healthDataService.buildEnrichedHealthData(eq(mockHealthData), eq(userId), any()))
                    .willReturn(mockEnrichedHealthData);
            given(weatherClient.getCurrentWeather()).willReturn(mockWeatherData);

            // when
            UserContext result = contextService.initializeContext(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getDate()).isEqualTo(LocalDate.now());
            assertThat(result.getPreferences()).isEqualTo(mockPreferences);
            assertThat(result.getEnrichedHealthData()).isEqualTo(mockEnrichedHealthData);
            assertThat(result.getTodayWeather()).isEqualTo(mockWeatherData);
            assertThat(result.isActive()).isTrue();
            assertThat(result.getConversationHistory()).isEmpty();

            // 저장 확인: getContext로 조회 가능해야 함
            UserContext stored = contextService.getContext(userId);
            assertThat(stored).isEqualTo(result);
        }

        @Test
        @DisplayName("성공: healthData와 함께 UserContext를 생성하고 저장한다")
        void success_createsAndStoresContext_withHealthData() {
            // given
            given(userService.getPreferences(userId)).willReturn(mockPreferences);
            given(healthDataService.buildEnrichedHealthData(eq(mockHealthData), eq(userId), any()))
                    .willReturn(mockEnrichedHealthData);
            given(weatherClient.getCurrentWeather()).willReturn(mockWeatherData);

            // when
            UserContext result = contextService.initializeContext(userId, mockHealthData);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
            assertThat(result.getDate()).isEqualTo(LocalDate.now());
            assertThat(result.getPreferences()).isEqualTo(mockPreferences);
            assertThat(result.getEnrichedHealthData()).isEqualTo(mockEnrichedHealthData);
            assertThat(result.getTodayWeather()).isEqualTo(mockWeatherData);
            assertThat(result.isActive()).isTrue();
            assertThat(result.getConversationHistory()).isEmpty();

            // ContextService는 더 이상 건강 데이터 저장을 담당하지 않음 (ConversationService에서 처리)
        }

        @Test
        @DisplayName("성공: 외부 서비스들이 올바르게 호출된다 (healthData 제공 시)")
        void success_callsExternalServices_withHealthData() {
            // given
            given(userService.getPreferences(userId)).willReturn(mockPreferences);
            given(healthDataService.buildEnrichedHealthData(eq(mockHealthData), eq(userId), any()))
                    .willReturn(mockEnrichedHealthData);
            given(weatherClient.getCurrentWeather()).willReturn(mockWeatherData);

            // when
            contextService.initializeContext(userId, mockHealthData);

            // then (건강 데이터 저장은 ConversationService에서 담당)
            then(userService).should(times(1)).getPreferences(userId);
            then(healthDataService).should(times(1)).buildEnrichedHealthData(eq(mockHealthData), eq(userId), any());
            then(weatherClient).should(times(1)).getCurrentWeather();
        }

        @Test
        @DisplayName("성공: 외부 서비스들이 올바르게 호출된다 (healthData null)")
        void success_callsExternalServices_withoutHealthData() {
            // given
            given(userService.getPreferences(userId)).willReturn(mockPreferences);
            given(healthDataService.getTodayHealthData(userId)).willReturn(mockHealthData);
            given(healthDataService.buildEnrichedHealthData(eq(mockHealthData), eq(userId), any()))
                    .willReturn(mockEnrichedHealthData);
            given(weatherClient.getCurrentWeather()).willReturn(mockWeatherData);

            // when
            contextService.initializeContext(userId);

            // then (건강 데이터 저장은 ConversationService에서 담당)
            then(userService).should(times(1)).getPreferences(userId);
            then(healthDataService).should(times(1)).getTodayHealthData(userId);
            then(healthDataService).should(times(1)).buildEnrichedHealthData(eq(mockHealthData), eq(userId), any());
            then(weatherClient).should(times(1)).getCurrentWeather();
        }
    }

    @Nested
    @DisplayName("getContext 메서드")
    class GetContext {

        @Test
        @DisplayName("성공: 저장된 Context를 조회한다")
        void success_returnsStoredContext() {
            // given
            given(userService.getPreferences(userId)).willReturn(mockPreferences);
            given(healthDataService.buildEnrichedHealthData(eq(mockHealthData), eq(userId), any()))
                    .willReturn(mockEnrichedHealthData);
            given(weatherClient.getCurrentWeather()).willReturn(mockWeatherData);

            contextService.initializeContext(userId, mockHealthData);

            // when
            UserContext result = contextService.getContext(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("실패: 존재하지 않는 userId로 조회 시 예외 발생")
        void fail_throwsExceptionWhenContextNotFound() {
            // given
            Long nonExistentUserId = 999L;

            // when & then
            assertThatThrownBy(() -> contextService.getContext(nonExistentUserId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Context not found");
        }
    }

    @Nested
    @DisplayName("addConversationTurn 메서드")
    class AddConversationTurn {

        @Test
        @DisplayName("성공: 대화 턴을 히스토리에 추가한다")
        void success_addsConversationTurn() {
            // given
            given(userService.getPreferences(userId)).willReturn(mockPreferences);
            given(healthDataService.buildEnrichedHealthData(eq(mockHealthData), eq(userId), any()))
                    .willReturn(mockEnrichedHealthData);
            given(weatherClient.getCurrentWeather()).willReturn(mockWeatherData);

            contextService.initializeContext(userId, mockHealthData);

            String userMessage = "오늘 날씨가 좋네요";
            String aiResponse = "네, 정말 좋은 날씨예요!";

            // when
            contextService.addConversationTurn(userId, userMessage, aiResponse);

            // then
            UserContext context = contextService.getContext(userId);
            assertThat(context.getConversationHistory()).hasSize(1);
            assertThat(context.getConversationHistory().get(0).getUserMessage()).isEqualTo(userMessage);
            assertThat(context.getConversationHistory().get(0).getAiResponse()).isEqualTo(aiResponse);
            assertThat(context.getConversationHistory().get(0).getTimestamp()).isNotNull();
        }

        @Test
        @DisplayName("성공: 여러 대화 턴을 순서대로 추가한다")
        void success_addsMultipleConversationTurns() {
            // given
            given(userService.getPreferences(userId)).willReturn(mockPreferences);
            given(healthDataService.buildEnrichedHealthData(eq(mockHealthData), eq(userId), any()))
                    .willReturn(mockEnrichedHealthData);
            given(weatherClient.getCurrentWeather()).willReturn(mockWeatherData);

            contextService.initializeContext(userId, mockHealthData);

            // when
            contextService.addConversationTurn(userId, "첫 번째 메시지", "첫 번째 응답");
            contextService.addConversationTurn(userId, "두 번째 메시지", "두 번째 응답");
            contextService.addConversationTurn(userId, "세 번째 메시지", "세 번째 응답");

            // then
            UserContext context = contextService.getContext(userId);
            assertThat(context.getConversationHistory()).hasSize(3);
            assertThat(context.getConversationHistory().get(0).getUserMessage()).isEqualTo("첫 번째 메시지");
            assertThat(context.getConversationHistory().get(2).getUserMessage()).isEqualTo("세 번째 메시지");
        }
    }

    @Nested
    @DisplayName("finalizeContext 메서드")
    class FinalizeContext {

        @Test
        @DisplayName("성공: Context를 제거한다")
        void success_removesContext() {
            // given
            given(userService.getPreferences(userId)).willReturn(mockPreferences);
            given(healthDataService.buildEnrichedHealthData(eq(mockHealthData), eq(userId), any()))
                    .willReturn(mockEnrichedHealthData);
            given(weatherClient.getCurrentWeather()).willReturn(mockWeatherData);

            contextService.initializeContext(userId, mockHealthData);

            // when
            contextService.finalizeContext(userId);

            // then
            assertThatThrownBy(() -> contextService.getContext(userId))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Context not found");
        }

        @Test
        @DisplayName("성공: 존재하지 않는 Context 정리 시도해도 예외 발생하지 않음")
        void success_noExceptionWhenContextNotExists() {
            // given
            Long nonExistentUserId = 999L;

            // when & then (예외 발생하지 않아야 함)
            assertThatCode(() -> contextService.finalizeContext(nonExistentUserId))
                    .doesNotThrowAnyException();
        }
    }
}