package com.example.echo.conversation.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.UserContext;
import com.example.echo.context.service.ContextService;
import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.diary.service.DiaryService;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.memory.service.MemoryRecallService;
import com.example.echo.memory.service.MemoryService;
import com.example.echo.prompt.service.PromptService;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.service.VoiceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * 대화 시작 준비(prepareGreetingContext)는 공용 풀(applicationTaskExecutor)을 쓴다. 공용 풀은 스레드 8개·대기열 무제한이라
 * 스레드가 모두 차면 더 늘어나지 않는다. 그 상태에서 풀 스레드가 같은 풀의 작업을 기다리면 영원히 멈춘다.
 * 스레드 1개짜리 풀로 "공용 풀이 다 찬" 상황을 만들어 확인한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationService - 대화 시작 준비가 공용 스레드풀 안에서 서로를 기다리며 멈추지 않는다")
class ConversationStartExecutorTest {

    private static final Long USER_ID = 1L;

    /** 스레드 1개 = 공용 풀의 스레드가 모두 차 있는 상황 (멈춘 스레드가 테스트 JVM 종료를 막지 않도록 데몬) */
    private final ExecutorService sharedPool = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "shared-pool-1");
        thread.setDaemon(true);
        return thread;
    });

    @Spy
    private TaskExecutor taskExecutor = new ConcurrentTaskExecutor(sharedPool);
    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();
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
    @Mock
    private MemoryRecallService memoryRecallService;
    @Mock
    private SpeechStreamPipeline speechStreamPipeline;

    @InjectMocks
    private ConversationService conversationService;

    @AfterEach
    void tearDown() {
        sharedPool.shutdownNow();
    }

    @Test
    @DisplayName("컨텍스트 준비가 같은 풀에 작업을 넣고 기다려도(LocationService의 방문 장소 처리) 대화 시작이 끝난다")
    void contextInitWaitingOnSharedPool_doesNotDeadlock() {
        UserContext context = UserContext.builder()
                .userId(USER_ID)
                .conversationHistory(new CopyOnWriteArrayList<>())
                .preferences(UserPreferences.builder()
                        .userId(USER_ID)
                        .voiceSettings(VoiceSettings.builder().build())
                        .build())
                .build();
        // LocationService.enrichLocationData와 같은 동작: 같은 풀에 장소별 작업을 넣고 끝날 때까지 기다린다
        when(contextService.initializeContext(eq(USER_ID), any(), any()))
                .thenAnswer(invocation -> CompletableFuture.supplyAsync(() -> context, taskExecutor).join());
        when(promptService.buildSystemPrompt(context)).thenReturn("시스템 프롬프트");
        when(aiService.generateGreeting(any(), eq(context))).thenReturn("안녕하세요!");
        when(voiceService.textToSpeech(any(), any())).thenReturn(new byte[]{1});

        ConversationStartResponse response = assertTimeoutPreemptively(Duration.ofSeconds(5),
                () -> conversationService.startConversation(USER_ID, HealthData.builder().steps(1000).build(), null),
                "공용 풀 스레드가 같은 풀의 작업을 기다리며 멈춤");

        assertThat(response.getMessage()).isEqualTo("안녕하세요!");
    }
}
