package com.example.echo.conversation.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.context.service.ContextService;
import com.example.echo.conversation.dto.ConversationResponse;
import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.diary.service.DiaryService;
import com.example.echo.prompt.service.PromptService;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.service.VoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
        import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationService 테스트2 - processUserMessage, endConversation")
class ConversationServiceTest2 {

    @InjectMocks
    private ConversationService conversationService;

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

    private Long userId;
    private UserContext mockContext;
    private VoiceSettings mockVoiceSettings;

    @BeforeEach
    void setUp() {
        userId = 1L;

        mockVoiceSettings = VoiceSettings.builder()
                .voiceSpeed(1.0)
                .voiceTone("warm")
                .build();

        UserPreferences mockPreferences = UserPreferences.builder()
                .userId(userId)
                .name("홍길동")
                .age(70)
                .voiceSettings(mockVoiceSettings)
                .build();

        mockContext = UserContext.builder()
                .userId(userId)
                .date(LocalDate.now())
                .conversationHistory(new ArrayList<>())
                .preferences(mockPreferences)
                .lastAccessTime(LocalDateTime.now())
                .isActive(true)
                .build();
    }

    @Nested
    @DisplayName("startConversation 메서드")
    class StartConversation {

        @Test
        @DisplayName("성공: 대화를 시작하고 인사말과 음성을 반환한다")
        void success_startsConversationWithGreeting() {
            // given
            String systemPrompt = "시스템 프롬프트";
            String greeting = "안녕하세요, 오늘 하루는 어떠셨나요?";
            byte[] audioData = "mock audio data".getBytes();

            given(contextService.initializeContext(userId)).willReturn(mockContext);
            given(promptService.buildSystemPrompt(mockContext)).willReturn(systemPrompt);
            given(aiService.generateGreeting(systemPrompt, mockContext)).willReturn(greeting);
            given(voiceService.textToSpeech(greeting, mockVoiceSettings)).willReturn(audioData);

            // when
            ConversationStartResponse result = conversationService.startConversation(userId);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getMessage()).isEqualTo(greeting);
            assertThat(result.getAudioData()).isEqualTo(audioData);
        }

        @Test
        @DisplayName("성공: 올바른 순서로 서비스들이 호출된다")
        void success_callsServicesInCorrectOrder() {
            // given
            String systemPrompt = "시스템 프롬프트";
            String greeting = "안녕하세요!";
            byte[] audioData = "audio".getBytes();

            given(contextService.initializeContext(userId)).willReturn(mockContext);
            given(promptService.buildSystemPrompt(mockContext)).willReturn(systemPrompt);
            given(aiService.generateGreeting(systemPrompt, mockContext)).willReturn(greeting);
            given(voiceService.textToSpeech(greeting, mockVoiceSettings)).willReturn(audioData);

            // when
            conversationService.startConversation(userId);

            // then (순서대로 호출 검증)
            var inOrder = inOrder(contextService, promptService, aiService, voiceService);
            inOrder.verify(contextService).initializeContext(userId);
            inOrder.verify(promptService).buildSystemPrompt(mockContext);
            inOrder.verify(aiService).generateGreeting(systemPrompt, mockContext);
            inOrder.verify(voiceService).textToSpeech(greeting, mockVoiceSettings);
        }
    }

    @Nested
    @DisplayName("processUserMessage 메서드")
    class ProcessUserMessage {

        @Test
        @DisplayName("성공: 사용자 음성을 처리하고 AI 응답을 반환한다")
        void success_processesUserMessageAndReturnsResponse() {
            // given
            MultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.mp3",
                    "audio/mpeg",
                    "mock audio content".getBytes()
            );

            String userMessage = "오늘 날씨가 좋아서 산책했어요";
            String conversationPrompt = "대화 프롬프트";
            String aiResponse = "산책하셨군요! 어디를 다녀오셨나요?";
            byte[] responseAudio = "response audio".getBytes();

            given(contextService.getContext(userId)).willReturn(mockContext);
            given(voiceService.speechToText(audioFile)).willReturn(userMessage);
            given(promptService.buildConversationPrompt(mockContext, userMessage)).willReturn(conversationPrompt);
            given(aiService.generateResponse(conversationPrompt)).willReturn(aiResponse);
            given(voiceService.textToSpeech(aiResponse, mockVoiceSettings)).willReturn(responseAudio);

            // when
            ConversationResponse result = conversationService.processUserMessage(userId, audioFile);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getUserMessage()).isEqualTo(userMessage);
            assertThat(result.getAiResponse()).isEqualTo(aiResponse);
            assertThat(result.getAudioData()).isEqualTo(responseAudio);
        }

        @Test
        @DisplayName("성공: STT → AI → TTS 순서로 처리된다")
        void success_processesInCorrectOrder() {
            // given
            MultipartFile audioFile = new MockMultipartFile(
                    "audio", "test.mp3", "audio/mpeg", "audio".getBytes()
            );

            String userMessage = "테스트 메시지";
            String prompt = "프롬프트";
            String aiResponse = "AI 응답";
            byte[] audio = "audio".getBytes();

            given(contextService.getContext(userId)).willReturn(mockContext);
            given(voiceService.speechToText(audioFile)).willReturn(userMessage);
            given(promptService.buildConversationPrompt(mockContext, userMessage)).willReturn(prompt);
            given(aiService.generateResponse(prompt)).willReturn(aiResponse);
            given(voiceService.textToSpeech(aiResponse, mockVoiceSettings)).willReturn(audio);

            // when
            conversationService.processUserMessage(userId, audioFile);

            // then
            var inOrder = inOrder(contextService, voiceService, promptService, aiService);
            inOrder.verify(contextService).getContext(userId);
            inOrder.verify(voiceService).speechToText(audioFile);
            inOrder.verify(promptService).buildConversationPrompt(mockContext, userMessage);
            inOrder.verify(aiService).generateResponse(prompt);
            inOrder.verify(voiceService).textToSpeech(aiResponse, mockVoiceSettings);
        }

        @Test
        @DisplayName("실패: Context가 없으면 예외 발생")
        void fail_throwsExceptionWhenContextNotFound() {
            // given
            MultipartFile audioFile = new MockMultipartFile(
                    "audio", "test.mp3", "audio/mpeg", "audio".getBytes()
            );

            given(contextService.getContext(userId))
                    .willThrow(new IllegalStateException("Context not found"));

            // when & then
            assertThatThrownBy(() -> conversationService.processUserMessage(userId, audioFile))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Context not found");
        }
    }

    @Nested
    @DisplayName("endConversation 메서드")
    class EndConversation {

        @Test
        @DisplayName("성공: 대화를 종료하고 컨텍스트를 정리한다")
        void success_endsConversationAndCleansUp() {
            // given
            mockContext.getConversationHistory().add(
                    ConversationTurn.builder()
                            .userMessage("테스트")
                            .aiResponse("응답")
                            .timestamp(LocalDateTime.now())
                            .build()
            );

            given(contextService.getContext(userId)).willReturn(mockContext);
            willDoNothing().given(contextService).finalizeContext(userId);

            // when
            conversationService.endConversation(userId);

            // then
            then(contextService).should().getContext(userId);
            then(contextService).should().finalizeContext(userId);
        }

        @Test
        @DisplayName("성공: Context 조회 후 정리 순서가 올바르다")
        void success_correctOrderOfOperations() {
            // given
            given(contextService.getContext(userId)).willReturn(mockContext);
            willDoNothing().given(contextService).finalizeContext(userId);

            // when
            conversationService.endConversation(userId);

            // then
            var inOrder = inOrder(contextService);
            inOrder.verify(contextService).getContext(userId);
            inOrder.verify(contextService).finalizeContext(userId);
        }
    }
}