package com.example.echo.conversation.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.context.service.ContextService;
import com.example.echo.conversation.dto.ConversationEndResponse;
import com.example.echo.conversation.dto.ConversationResponse;
import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.diary.entity.Diary;
import com.example.echo.diary.entity.DiaryStatus;
import com.example.echo.diary.service.DiaryOutcome;
import com.example.echo.diary.service.DiaryService;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.service.MemoryService;
import com.example.echo.prompt.service.PromptService;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.service.VoiceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private HealthDataService healthDataService;

    @Mock
    private MemoryService memoryService;

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

            given(contextService.initializeContext(eq(userId), any(), any())).willReturn(mockContext);
            given(promptService.buildSystemPrompt(mockContext)).willReturn(systemPrompt);
            given(aiService.generateGreeting(systemPrompt, mockContext)).willReturn(greeting);
            given(voiceService.textToSpeech(greeting, mockVoiceSettings)).willReturn(audioData);

            // when
            ConversationStartResponse result = conversationService.startConversation(userId, null, null);

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

            given(contextService.initializeContext(eq(userId), any(), any())).willReturn(mockContext);
            given(promptService.buildSystemPrompt(mockContext)).willReturn(systemPrompt);
            given(aiService.generateGreeting(systemPrompt, mockContext)).willReturn(greeting);
            given(voiceService.textToSpeech(greeting, mockVoiceSettings)).willReturn(audioData);

            // when
            conversationService.startConversation(userId, null, null);

            // then (순서대로 호출 검증)
            var inOrder = inOrder(contextService, promptService, aiService, voiceService);
            inOrder.verify(contextService).initializeContext(eq(userId), any(), any());
            inOrder.verify(promptService).buildSystemPrompt(mockContext);
            inOrder.verify(aiService).generateGreeting(systemPrompt, mockContext);
            inOrder.verify(voiceService).textToSpeech(greeting, mockVoiceSettings);
        }

        @Test
        @DisplayName("성공: 저장된 장기기억이 시스템 프롬프트에 주입된다")
        void success_injectsLifeMemoriesIntoSystemPrompt() {
            // given
            String systemPrompt = "시스템 프롬프트";
            given(contextService.initializeContext(eq(userId), any(), any())).willReturn(mockContext);
            given(promptService.buildSystemPrompt(mockContext)).willReturn(systemPrompt);
            given(memoryService.getMemories(userId)).willReturn(java.util.List.of(
                    Memory.builder()
                            .userId(userId)
                            .lifePeriod("청년기")
                            .topic("직업")
                            .content("30대에 부산에서 어부로 일했다")
                            .tags("부산,어부")
                            .build()
            ));
            given(aiService.generateGreeting(anyString(), eq(mockContext))).willReturn("안녕하세요!");
            given(voiceService.textToSpeech(anyString(), eq(mockVoiceSettings))).willReturn("audio".getBytes());

            // when
            conversationService.startConversation(userId, null, null);

            // then: 원본 프롬프트 뒤에 기억 섹션이 덧붙어 AI에 전달되어야 함
            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            then(aiService).should().generateGreeting(promptCaptor.capture(), eq(mockContext));

            String finalPrompt = promptCaptor.getValue();
            assertThat(finalPrompt).startsWith(systemPrompt);
            assertThat(finalPrompt).contains("[어르신의 지난 이야기");
            assertThat(finalPrompt).contains("30대에 부산에서 어부로 일했다");
            assertThat(finalPrompt).contains("부산,어부");
        }

        @Test
        @DisplayName("성공: 장기기억 조회가 실패해도 원본 프롬프트로 대화를 시작한다")
        void memoryLookupFailure_startsConversationWithOriginalPrompt() {
            // given
            String systemPrompt = "시스템 프롬프트";
            given(contextService.initializeContext(eq(userId), any(), any())).willReturn(mockContext);
            given(promptService.buildSystemPrompt(mockContext)).willReturn(systemPrompt);
            given(memoryService.getMemories(userId)).willThrow(new RuntimeException("DB 연결 끊김"));
            given(aiService.generateGreeting(systemPrompt, mockContext)).willReturn("안녕하세요!");
            given(voiceService.textToSpeech(anyString(), eq(mockVoiceSettings))).willReturn("audio".getBytes());

            // when
            ConversationStartResponse result = conversationService.startConversation(userId, null, null);

            // then
            assertThat(result.getMessage()).isEqualTo("안녕하세요!");
            then(aiService).should().generateGreeting(systemPrompt, mockContext);
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
            String systemPrompt = "시스템 프롬프트";
            String aiResponse = "산책하셨군요! 어디를 다녀오셨나요?";
            byte[] responseAudio = "response audio".getBytes();

            // Context에 systemPrompt 설정
            mockContext.setSystemPrompt(systemPrompt);

            given(contextService.getContext(userId)).willReturn(mockContext);
            given(voiceService.speechToText(audioFile)).willReturn(userMessage);
            // OpenAI 권장 방식: messages 배열로 직접 전달
            given(aiService.generateResponse(eq(systemPrompt), any(), eq(userMessage))).willReturn(aiResponse);
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
        @DisplayName("성공: STT → AI → TTS 순서로 처리된다 (messages 배열 방식)")
        void success_processesInCorrectOrder() {
            // given
            MultipartFile audioFile = new MockMultipartFile(
                    "audio", "test.mp3", "audio/mpeg", "audio".getBytes()
            );

            String userMessage = "테스트 메시지";
            String systemPrompt = "시스템 프롬프트";
            String aiResponse = "AI 응답";
            byte[] audio = "audio".getBytes();

            mockContext.setSystemPrompt(systemPrompt);

            given(contextService.getContext(userId)).willReturn(mockContext);
            given(voiceService.speechToText(audioFile)).willReturn(userMessage);
            given(aiService.generateResponse(eq(systemPrompt), any(), eq(userMessage))).willReturn(aiResponse);
            given(voiceService.textToSpeech(aiResponse, mockVoiceSettings)).willReturn(audio);

            // when
            conversationService.processUserMessage(userId, audioFile);

            // then (PromptService.buildConversationPrompt 호출 제거됨)
            var inOrder = inOrder(contextService, voiceService, aiService);
            inOrder.verify(contextService).getContext(userId);
            inOrder.verify(voiceService).speechToText(audioFile);
            inOrder.verify(aiService).generateResponse(eq(systemPrompt), any(), eq(userMessage));
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

        @Test
        @DisplayName("성공: 일기 생성 성공 시 응답에 diaryStatus=SUCCESS와 diaryId가 담긴다")
        void success_responseContainsDiaryStatus() {
            // given
            LocalDate diaryDate = LocalDate.now();
            Diary diary = Diary.builder()
                    .userId(userId)
                    .diaryDate(diaryDate)
                    .content("오늘의 일기")
                    .status(DiaryStatus.SUCCESS)
                    .build();
            given(contextService.getContext(userId)).willReturn(mockContext);
            given(diaryService.generateAndSaveDiary(mockContext)).willReturn(new DiaryOutcome.Processed(diary));
            willDoNothing().given(contextService).finalizeContext(userId);

            // when
            ConversationEndResponse response = conversationService.endConversation(userId);

            // then
            assertThat(response.getDiaryStatus()).isEqualTo("SUCCESS");
            assertThat(response.getDiaryError()).isNull();
            assertThat(response.getEndedAt()).isNotNull();
            // 클라이언트가 일기 탭 날짜 버킷을 서버 값으로 신뢰할 수 있도록 diaryDate가 그대로 담겨야 함
            assertThat(response.getDiaryDate()).isEqualTo(diaryDate);
        }

        @Test
        @DisplayName("성공: 일기 생성 실패 시 응답에 diaryStatus=FAILED와 실패 사유가 담기고 컨텍스트는 정리된다")
        void diaryFailure_responseContainsFailureAndContextCleaned() {
            // given
            Diary failedDiary = Diary.builder()
                    .userId(userId)
                    .diaryDate(LocalDate.now())
                    .status(DiaryStatus.FAILED)
                    .failureReason("AI 일기 생성 실패: 401")
                    .build();
            given(contextService.getContext(userId)).willReturn(mockContext);
            given(diaryService.generateAndSaveDiary(mockContext)).willReturn(new DiaryOutcome.Processed(failedDiary));
            willDoNothing().given(contextService).finalizeContext(userId);

            // when
            ConversationEndResponse response = conversationService.endConversation(userId);

            // then
            assertThat(response.getDiaryStatus()).isEqualTo("FAILED");
            assertThat(response.getDiaryError()).contains("AI 일기 생성 실패");
            then(contextService).should().finalizeContext(userId);
        }

        @Test
        @DisplayName("성공: 일기 생성이 스킵되면(사용자 발화 없음) diaryStatus=SKIPPED가 담긴다")
        void diarySkipped_responseContainsSkipped() {
            // given
            given(contextService.getContext(userId)).willReturn(mockContext);
            given(diaryService.generateAndSaveDiary(mockContext)).willReturn(new DiaryOutcome.Skipped());
            willDoNothing().given(contextService).finalizeContext(userId);

            // when
            ConversationEndResponse response = conversationService.endConversation(userId);

            // then
            assertThat(response.getDiaryStatus()).isEqualTo("SKIPPED");
            assertThat(response.getDiaryId()).isNull();
            assertThat(response.getDiaryError()).isNull();
            assertThat(response.getDiaryDate()).isNull();
        }

        @Test
        @DisplayName("성공: 대화 원문이 사라지기 전에 장기기억을 추출한다")
        void success_extractsMemoriesBeforeContextIsFinalized() {
            // given
            given(contextService.getContext(userId)).willReturn(mockContext);
            willDoNothing().given(contextService).finalizeContext(userId);

            // when
            conversationService.endConversation(userId);

            // then: finalizeContext가 대화 원문을 지우므로 추출이 반드시 그 전이어야 함
            var inOrder = inOrder(memoryService, contextService);
            inOrder.verify(memoryService).extractAndSaveMemories(mockContext);
            inOrder.verify(contextService).finalizeContext(userId);
        }

        @Test
        @DisplayName("성공: 장기기억 추출이 실패해도 일기 결과와 컨텍스트 정리에 영향이 없다")
        void memoryExtractionFailure_doesNotAffectDiaryOrCleanup() {
            // given
            Diary diary = Diary.builder()
                    .userId(userId)
                    .diaryDate(LocalDate.now())
                    .content("오늘의 일기")
                    .status(DiaryStatus.SUCCESS)
                    .build();
            given(contextService.getContext(userId)).willReturn(mockContext);
            given(diaryService.generateAndSaveDiary(mockContext)).willReturn(new DiaryOutcome.Processed(diary));
            willThrow(new RuntimeException("기억 추출 실패"))
                    .given(memoryService).extractAndSaveMemories(mockContext);
            willDoNothing().given(contextService).finalizeContext(userId);

            // when
            ConversationEndResponse response = conversationService.endConversation(userId);

            // then: 일기는 SUCCESS 그대로여야 하고(FAILED로 오염 금지), 컨텍스트도 정리되어야 함
            assertThat(response.getDiaryStatus()).isEqualTo("SUCCESS");
            assertThat(response.getDiaryError()).isNull();
            then(contextService).should().finalizeContext(userId);
        }
    }
}