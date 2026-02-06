package com.example.echo.conversation.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.common.dto.WeatherData;
import com.example.echo.context.domain.UserContext;
import com.example.echo.context.service.ContextService;
import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.diary.service.DiaryService;
import com.example.echo.health.dto.HealthData;
import com.example.echo.prompt.service.PromptService;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.service.VoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private VoiceService voiceService;

    @Mock
    private PromptService promptService;

    @Mock
    private AIService aiService;

    @Mock
    private ContextService contextService;

    @Mock
    private DiaryService diaryService;

    //@InjectMocks-제거
    private ConversationService conversationService;

    private UserContext mockContext;
    private final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // 직접 생성자로 주입
        conversationService = new ConversationService(
                voiceService,
                promptService,
                aiService,
                contextService,
                diaryService
        );
        mockContext = createMockContext();
    }

    @Test
    @DisplayName("startConversation - 정상 플로우: 컨텍스트 초기화 → 인사 생성 → TTS 변환 → 응답 반환")
    void startConversation_success() {
        // Given
        String systemPrompt = "당신은 친근한 대화 상대입니다.";
        String greeting = "안녕하세요! 오늘 하루는 어떠셨어요?";
        byte[] audioData = "mock-audio-data".getBytes();

        when(contextService.initializeContext(TEST_USER_ID)).thenReturn(mockContext);
        when(promptService.buildSystemPrompt(mockContext)).thenReturn(systemPrompt);
        when(aiService.generateGreeting(systemPrompt, mockContext)).thenReturn(greeting);
        when(voiceService.textToSpeech(eq(greeting), any(VoiceSettings.class))).thenReturn(audioData);

        // When
        ConversationStartResponse response = conversationService.startConversation(TEST_USER_ID);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getMessage()).isEqualTo(greeting);
        assertThat(response.getAudioData()).isEqualTo(audioData);
    }

    @Test
    @DisplayName("startConversation - 호출 순서 검증: 컨텍스트 → 프롬프트 → AI → TTS 순서로 호출")
    void startConversation_verifyCallOrder() {
        // Given
        String systemPrompt = "시스템 프롬프트";
        String greeting = "안녕하세요!";
        byte[] audioData = "audio".getBytes();

        when(contextService.initializeContext(TEST_USER_ID)).thenReturn(mockContext);
        when(promptService.buildSystemPrompt(mockContext)).thenReturn(systemPrompt);
        when(aiService.generateGreeting(systemPrompt, mockContext)).thenReturn(greeting);
        when(voiceService.textToSpeech(eq(greeting), any(VoiceSettings.class))).thenReturn(audioData);

        // When
        conversationService.startConversation(TEST_USER_ID);

        // Then - 순서 검증
        InOrder inOrder = inOrder(contextService, promptService, aiService, voiceService);
        inOrder.verify(contextService).initializeContext(TEST_USER_ID);
        inOrder.verify(promptService).buildSystemPrompt(mockContext);
        inOrder.verify(aiService).generateGreeting(eq(systemPrompt), eq(mockContext));
        inOrder.verify(voiceService).textToSpeech(eq(greeting), any(VoiceSettings.class));
    }

    @Test
    @DisplayName("startConversation - 각 의존성이 정확히 1번씩 호출됨")
    void startConversation_verifyEachDependencyCalledOnce() {
        // Given
        when(contextService.initializeContext(TEST_USER_ID)).thenReturn(mockContext);
        when(promptService.buildSystemPrompt(any())).thenReturn("prompt");
        when(aiService.generateGreeting(any(), any())).thenReturn("greeting");
        when(voiceService.textToSpeech(any(), any())).thenReturn("audio".getBytes());

        // When
        conversationService.startConversation(TEST_USER_ID);

        // Then
        verify(contextService, times(1)).initializeContext(TEST_USER_ID);
        verify(promptService, times(1)).buildSystemPrompt(mockContext);
        verify(aiService, times(1)).generateGreeting(any(), any());
        verify(voiceService, times(1)).textToSpeech(any(), any());
    }

    private UserContext createMockContext() {
        VoiceSettings voiceSettings = VoiceSettings.builder()
                .voiceSpeed(1.0)
                .voiceTone("warm")
                .build();

        UserPreferences preferences = UserPreferences.builder()
                .userId(TEST_USER_ID)
                .name("홍길동")
                .age(65)
                .location("서울")
                .voiceSettings(voiceSettings)
                .build();

        HealthData healthData = HealthData.builder()
                .steps(5000)
                .sleepDurationMinutes(420)
                .build();

        WeatherData weatherData = WeatherData.builder()
                .description("맑음")
                .temperature(20)
                .build();

        return UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(preferences)
                .todayHealthData(healthData)
                .todayWeather(weatherData)
                .build();
    }
}