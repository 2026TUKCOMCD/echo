package com.example.echo.conversation.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.ai.service.ModelRotationService;
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
import com.example.echo.memory.service.RecallTopicRotationService;
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
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
    private ModelRotationService modelRotationService;

    @Mock
    private ContextService contextService;

    @Mock
    private DiaryService diaryService;

    @Mock
    private HealthDataService healthDataService;

    @Mock
    private MemoryService memoryService;

    @Mock
    private RecallTopicRotationService recallTopicRotationService;

    @Mock
    private TaskExecutor taskExecutor;

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
            given(promptService.buildSystemPrompt(eq(mockContext), any(), any())).willReturn(systemPrompt);
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
            given(promptService.buildSystemPrompt(eq(mockContext), any(), any())).willReturn(systemPrompt);
            given(aiService.generateGreeting(systemPrompt, mockContext)).willReturn(greeting);
            given(voiceService.textToSpeech(greeting, mockVoiceSettings)).willReturn(audioData);

            // when
            conversationService.startConversation(userId, null, null);

            // then (순서대로 호출 검증)
            var inOrder = inOrder(contextService, promptService, aiService, voiceService);
            inOrder.verify(contextService).initializeContext(eq(userId), any(), any());
            inOrder.verify(promptService).buildSystemPrompt(eq(mockContext), any(), any());
            inOrder.verify(aiService).generateGreeting(systemPrompt, mockContext);
            inOrder.verify(voiceService).textToSpeech(greeting, mockVoiceSettings);
        }

        @Test
        @DisplayName("성공: 저장된 장기기억과 오늘의 회상 주제가 시스템 프롬프트 생성에 전달된다")
        void success_passesLifeMemoriesToSystemPrompt() {
            // given
            Memory memory = Memory.builder()
                    .userId(userId)
                    .lifePeriod("청년기")
                    .topic("직업")
                    .content("30대에 부산에서 어부로 일했다")
                    .tags("부산,어부")
                    .build();
            String recallGuide = "[오늘의 회상 주제] 고향\n[발굴 모드] ...";
            given(contextService.initializeContext(eq(userId), any(), any())).willReturn(mockContext);
            given(promptService.buildSystemPrompt(eq(mockContext), any(), any())).willReturn("시스템 프롬프트");
            given(memoryService.getMemories(userId)).willReturn(List.of(memory));
            given(recallTopicRotationService.currentTopic()).willReturn("고향");
            given(promptService.buildRecallGuide(eq("고향"), any())).willReturn(recallGuide);
            given(aiService.generateGreeting(anyString(), eq(mockContext))).willReturn("안녕하세요!");
            given(voiceService.textToSpeech(anyString(), eq(mockVoiceSettings))).willReturn("audio".getBytes());

            // when
            conversationService.startConversation(userId, null, null);

            // then: 조회된 기억과 오늘의 회상 주제가 각각 {{lifeMemories}}/{{recallGuide}} 치환용으로 전달되어야 함
            ArgumentCaptor<List<Memory>> memoriesCaptor = ArgumentCaptor.forClass(List.class);
            ArgumentCaptor<String> recallGuideCaptor = ArgumentCaptor.forClass(String.class);
            then(promptService).should().buildSystemPrompt(
                    eq(mockContext), memoriesCaptor.capture(), recallGuideCaptor.capture());
            assertThat(memoriesCaptor.getValue()).containsExactly(memory);
            assertThat(recallGuideCaptor.getValue()).isEqualTo(recallGuide);
        }

        @Test
        @DisplayName("성공: 장기기억 조회가 실패해도 기억 없이 대화를 시작한다")
        void memoryLookupFailure_startsConversationWithoutMemories() {
            // given
            given(contextService.initializeContext(eq(userId), any(), any())).willReturn(mockContext);
            given(promptService.buildSystemPrompt(eq(mockContext), any(), any())).willReturn("시스템 프롬프트");
            given(memoryService.getMemories(userId)).willThrow(new RuntimeException("DB 연결 끊김"));
            given(aiService.generateGreeting(anyString(), eq(mockContext))).willReturn("안녕하세요!");
            given(voiceService.textToSpeech(anyString(), eq(mockVoiceSettings))).willReturn("audio".getBytes());

            // when
            ConversationStartResponse result = conversationService.startConversation(userId, null, null);

            // then: 대화는 정상 시작되고, 프롬프트는 빈 기억 목록으로 생성되어야 함
            assertThat(result.getMessage()).isEqualTo("안녕하세요!");
            ArgumentCaptor<List<Memory>> memoriesCaptor = ArgumentCaptor.forClass(List.class);
            then(promptService).should().buildSystemPrompt(eq(mockContext), memoriesCaptor.capture(), any());
            assertThat(memoriesCaptor.getValue()).isEmpty();
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
            given(aiService.generateResponse(eq(systemPrompt), any(), eq(userMessage), any())).willReturn(aiResponse);
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
            given(aiService.generateResponse(eq(systemPrompt), any(), eq(userMessage), any())).willReturn(aiResponse);
            given(voiceService.textToSpeech(aiResponse, mockVoiceSettings)).willReturn(audio);

            // when
            conversationService.processUserMessage(userId, audioFile);

            // then (PromptService.buildConversationPrompt 호출 제거됨)
            var inOrder = inOrder(contextService, voiceService, aiService);
            inOrder.verify(contextService).getContext(userId);
            inOrder.verify(voiceService).speechToText(audioFile);
            inOrder.verify(aiService).generateResponse(eq(systemPrompt), any(), eq(userMessage), any());
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
        @DisplayName("성공: 장기기억 추출은 컨텍스트 정리 전에 백그라운드로 제출되고, 원본과 분리된 스냅샷이 전달된다")
        void success_submitsMemoryExtractionBeforeContextIsFinalizedWithSnapshot() {
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

            // then: taskExecutor 제출이 finalizeContext보다 먼저여야 함 (제출 시점에 원본 대화 원문이 아직 살아있어야 함)
            var inOrder = inOrder(taskExecutor, contextService);
            inOrder.verify(taskExecutor).execute(any(Runnable.class));
            inOrder.verify(contextService).finalizeContext(userId);

            // 그리고: 백그라운드 작업이 실제로 실행되면, 넘겨받는 컨텍스트는 원본과 다른 인스턴스이되
            // 대화 원문은 그대로 담고 있어야 함 (finalizeContext가 원본을 지워도 영향받지 않도록)
            ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
            then(taskExecutor).should().execute(taskCaptor.capture());
            taskCaptor.getValue().run();

            ArgumentCaptor<UserContext> snapshotCaptor = ArgumentCaptor.forClass(UserContext.class);
            then(memoryService).should().extractAndSaveMemories(snapshotCaptor.capture());
            UserContext snapshot = snapshotCaptor.getValue();
            assertThat(snapshot).isNotSameAs(mockContext);
            assertThat(snapshot.getUserId()).isEqualTo(mockContext.getUserId());
            assertThat(snapshot.getConversationHistory()).isEqualTo(mockContext.getConversationHistory());
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
                    .given(memoryService).extractAndSaveMemories(any());
            willAnswer(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return null;
            }).given(taskExecutor).execute(any(Runnable.class));
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