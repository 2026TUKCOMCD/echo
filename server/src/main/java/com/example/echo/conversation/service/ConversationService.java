package com.example.echo.conversation.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.UserContext;
import com.example.echo.context.service.ContextService;
import com.example.echo.conversation.dto.ConversationEndResponse;
import com.example.echo.conversation.dto.ConversationResponse;
import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.conversation.dto.StreamedConversation;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.conversation.dto.TtsRetryResponse;
import com.example.echo.conversation.stream.SpeechSegment;
import com.example.echo.conversation.stream.SpeechSegmentSource;
import com.example.echo.conversation.exception.ConversationNotFoundException;
import com.example.echo.diary.entity.Diary;
import com.example.echo.diary.service.DiaryOutcome;
import com.example.echo.diary.service.DiaryService;
import com.example.echo.health.dto.HealthData;
import com.example.echo.location.dto.RawLocationData;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.memory.entity.Memory;
import com.example.echo.memory.service.MemoryService;
import com.example.echo.memory.service.RecallTopicRotationService;
import com.example.echo.prompt.service.PromptService;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.service.VoiceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    // STT 결과가 빈 문자열이면(무음·너무 짧은 녹음) AI를 호출하지 않고 바로 안내로 응답한다.
    // 빈 user 메시지를 그대로 보내면 일부 프로바이더(Gemini 계열)가 "메시지가 model 턴으로
    // 끝난다"며 400을 반환하는 문제가 있어, 애초에 빈 메시지를 만들지 않는 쪽으로 막는다.
    // 연속 횟수에 따라 문구를 단계적으로 바꾼다 - 시스템 프롬프트의 [이탈 발화 및 무응답 대응]과
    // 같은 3단계 패턴(1회 재요청 → 2회 화제 전환 제안 → 3회 마무리 유도).
    private static final String EMPTY_STT_RETRY = "죄송해요, 잘 못 들었어요. 다시 한 번 말씀해 주시겠어요?";
    private static final String EMPTY_STT_OFFER_TOPIC_SWITCH = "괜찮아요, 천천히 하셔도 돼요. 편하게 다른 이야기 해보셔도 좋아요.";
    private static final String EMPTY_STT_WRAP_UP = "오늘은 여기까지 이야기 나눌까요? 다음에 또 편하게 말씀해 주세요.";

    private static String emptySttFallbackResponse(int consecutiveCount) {
        if (consecutiveCount <= 1) {
            return EMPTY_STT_RETRY;
        } else if (consecutiveCount == 2) {
            return EMPTY_STT_OFFER_TOPIC_SWITCH;
        }
        return EMPTY_STT_WRAP_UP;
    }

    private final VoiceService voiceService;
    private final PromptService promptService;
    private final AIService aiService;
    private final ContextService contextService;
    private final DiaryService diaryService;
    private final HealthDataService healthDataService;
    private final MemoryService memoryService;
    private final RecallTopicRotationService recallTopicRotationService;
    // @EnableScheduling이 만드는 taskScheduler도 TaskExecutor라 타입만으로는 모호하다.
    // application.yaml의 spring.task.execution 설정을 받는 쪽을 명시적으로 지정한다.
    @Qualifier("applicationTaskExecutor")
    private final TaskExecutor taskExecutor;
    private final MeterRegistry meterRegistry;
    private final SpeechStreamPipeline speechStreamPipeline;

    /**
     * STT/LLM/TTS 등 파이프라인 구간의 소요 시간을 측정해 Micrometer 타이머(echo.conversation.stage,
     * tag: stage)로 기록하고 로그에도 남긴다. /actuator/metrics/echo.conversation.stage 로 조회 가능.
     *
     * 지연 개선(perf/reduce-conversation-latency) 작업의 baseline 측정용 - 이 값 없이는 이후 튜닝의
     * 효과를 증명할 수 없어 가장 먼저 추가함.
     *
     * historyTurns는 "히스토리가 길어질수록 llm 구간이 느려지는지" 실측 데이터를 쌓기 위한 필드다.
     * Micrometer 태그로는 넣지 않는다 - 턴 수만큼 카디널리티가 늘어나 지표 폭증으로 이어질 수 있어서,
     * 로그로만 남기고 상관관계 분석은 로그를 모아서 별도로 한다.
     */
    private <T> T timed(String stage, Long userId, int historyTurns, Supplier<T> action) {
        long start = System.currentTimeMillis();
        T result = action.get();
        long elapsedMs = System.currentTimeMillis() - start;
        Timer.builder("echo.conversation.stage")
                .description("대화 파이프라인 구간별 소요 시간")
                .tag("stage", stage)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
        log.info("[지연측정] stage={}, userId={}, elapsedMs={}, historyTurns={}", stage, userId, elapsedMs, historyTurns);
        return result;
    }

    /** timed()와 같은 타이머에 구간 전체 합산치(예: start_total)를 기록한다. */
    private void recordTotal(String stage, Long userId, long startedAtMs, int historyTurns) {
        long elapsedMs = System.currentTimeMillis() - startedAtMs;
        Timer.builder("echo.conversation.stage")
                .description("대화 파이프라인 구간별 소요 시간")
                .tag("stage", stage)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
        log.info("[지연측정] stage={}, userId={}, elapsedMs={}, historyTurns={}", stage, userId, elapsedMs, historyTurns);
    }

    // 첫 인사와 메시지 턴의 LLM/TTS는 길이·성격이 달라 평균이 섞이지 않도록 stage 이름을 분리한다.
    // llm_greeting / llm 은 스트리밍 전과 같은 의미(LLM 전체 소요)로 유지해 전후 비교가 가능하게 한다.
    private static final SpeechStreamPipeline.Stages GREETING_STAGES = new SpeechStreamPipeline.Stages(
            "start_llm_first_token", "start_llm_first_chunk", "llm_greeting",
            "start_tts_first_byte", "start_tts_rest_first_byte");
    private static final SpeechStreamPipeline.Stages MESSAGE_STAGES = new SpeechStreamPipeline.Stages(
            "llm_first_token", "llm_first_chunk", "llm",
            "tts_first_byte", "tts_rest_first_byte");

    /** 이미 잰 구간 시간을 timed()와 같은 타이머/로그 형식으로 기록한다 */
    private void recordElapsed(String stage, Long userId, long elapsedMs, int historyTurns) {
        Timer.builder("echo.conversation.stage")
                .description("대화 파이프라인 구간별 소요 시간")
                .tag("stage", stage)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
        log.info("[지연측정] stage={}, userId={}, elapsedMs={}, historyTurns={}", stage, userId, elapsedMs, historyTurns);
    }

    public ConversationStartResponse startConversation(Long userId, HealthData healthData, RawLocationData rawLocationData) {
        long turnStart = System.currentTimeMillis();
        UserContext context = prepareGreetingContext(userId, healthData, rawLocationData);

        // 4. 첫 인사 생성
        String firstMessage = timed("llm_greeting", userId, context.getConversationHistory().size(),
                () -> aiService.generateGreeting(context.getSystemPrompt(), context));

        // 5. TTS 변환
        byte[] audioData = timed("tts", userId, context.getConversationHistory().size(),
                () -> voiceService.textToSpeech(firstMessage, context.getPreferences().getVoiceSettings()));

        // 6. 히스토리 추가 (동기 - tts-retry에서 히스토리 조회 보장)
        contextService.addConversationTurn(userId, null, firstMessage);

        recordTotal("start_total", userId, turnStart, context.getConversationHistory().size());

        return ConversationStartResponse.builder()
                .message(firstMessage)
                .audioData(audioData)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * startConversation의 스트리밍 버전 - 첫 인사를 LLM이 생성하는 대로 조각 단위로 TTS해, 첫 조각의 오디오 첫 바이트가
     * 도착하면 바로 반환한다. 나머지 조각과 히스토리 저장은 {@link SpeechStreamPipeline}이 이어서 처리한다.
     *
     * 첫 인사에는 사용자 발화가 없으므로 StreamedConversation.userMessage는 항상 null이다.
     */
    public StreamedConversation startConversationStream(Long userId, HealthData healthData, RawLocationData rawLocationData) {
        long turnStart = System.currentTimeMillis();
        UserContext context = prepareGreetingContext(userId, healthData, rawLocationData);
        int historyTurns = context.getConversationHistory().size();

        SpeechStreamPipeline.Started started = speechStreamPipeline.start(
                () -> aiService.openGreetingStream(context.getSystemPrompt(), context),
                context.getPreferences().getVoiceSettings(),
                GREETING_STAGES,
                (stage, elapsedMs) -> recordElapsed(stage, userId, elapsedMs, historyTurns),
                greeting -> contextService.addConversationTurn(userId, null, greeting));

        recordTotal("start_first_audio", userId, turnStart, historyTurns);

        return new StreamedConversation(
                null,
                started.segments(),
                () -> recordTotal("start_tts_stream_total", userId, started.ttsStartedAtMs(), historyTurns)
        );
    }

    /** appendRecentDiaries에서 조회하는 최근 일기 범위 (일) */
    private static final int RECENT_DIARY_DAYS = 7;

    /**
     * 건강 데이터 저장 → 컨텍스트 초기화 → 시스템 프롬프트 생성 (일괄/스트리밍 공통 구간). 첫 인사 생성은 호출 방식별로 한다.
     *
     * 컨텍스트 초기화(위치/날씨 포함)·장기기억 조회·최근 일기 조회는 서로 결과를 참조하지 않는
     * 독립적인 읽기라, 순차로 하나씩 기다리는 대신 한꺼번에 병렬로 실행하고 다같이 기다린다.
     *
     * 건강데이터 저장은 이 병렬 배치에 넣지 않고 맨 앞에서 동기로 먼저 끝낸다 - 이건 "읽기"가 아니라
     * "쓰기 + 부작용"이라 성격이 다르다. 컨텍스트 초기화와 병렬로 돌리면, 저장이 실패해도 컨텍스트
     * 초기화는 멈추지 않고 끝까지 실행돼 ContextService의 세션 저장소에 절반만 완성된(systemPrompt가
     * 없는) 컨텍스트를 남기게 된다 - CompletableFuture는 형제 작업이 실패해도 나머지를 자동으로
     * 취소해주지 않기 때문이다. 원래 순차 코드가 "저장 실패 시 그 자리에서 전체 중단"을 보장했던
     * 것과 같은 보장을 유지하기 위해 이 순서를 지킨다.
     */
    private UserContext prepareGreetingContext(Long userId, HealthData healthData, RawLocationData rawLocationData) {
        // 0. 건강 데이터 저장 (Android에서 수신한 경우) - 실패하면 즉시 중단
        if (healthData != null) {
            healthDataService.saveHealthData(userId, healthData);
        }

        // 1. 독립적인 세 읽기 작업을 한꺼번에 제출
        CompletableFuture<UserContext> contextFuture = CompletableFuture.supplyAsync(
                () -> contextService.initializeContext(userId, healthData, rawLocationData), taskExecutor);
        CompletableFuture<List<Memory>> memoriesFuture = CompletableFuture.supplyAsync(
                () -> loadLifeMemories(userId), taskExecutor);
        CompletableFuture<List<Diary>> diariesFuture = CompletableFuture.supplyAsync(
                () -> loadRecentDiaries(userId), taskExecutor);

        UserContext context = join(contextFuture);
        List<Memory> lifeMemories = join(memoriesFuture);
        List<Diary> recentDiaries = join(diariesFuture);

        // 2. 오늘의 장기기억 회상 주제(3단계용) 확정
        // 시스템 프롬프트는 대화 시작 시 1회 생성되어 세션 내내 재사용되므로, 여기서 확정한 주제가
        // 자정을 넘겨도 세션 중에는 그대로 유지된다. 시스템 프롬프트에 구워 넣어야 하니 3보다 먼저 계산한다.
        String recallTopic = recallTopicRotationService.currentTopic();
        String recallGuide = promptService.buildRecallGuide(recallTopic, lifeMemories);

        // 3. 시스템 프롬프트 생성 및 컨텍스트에 캐싱 (processUserMessage에서 재사용)
        // 장기기억·오늘의 회상 주제는 템플릿 변수로, 최근 7일 일기는 뒤에 덧붙여
        // AI가 이전 대화를 기억하는 것처럼 이어가게 함
        String systemPrompt = appendRecentDiaries(
                promptService.buildSystemPrompt(context, lifeMemories, recallGuide), recentDiaries);
        context.setSystemPrompt(systemPrompt);
        return context;
    }

    /**
     * future.join()이 던지는 CompletionException을 벗겨 원래 예외를 다시 던진다.
     *
     * CompletionException으로 그대로 전파하면 GlobalExceptionHandler의 예외 타입 기반 매핑
     * (예: BaseException → 지정된 HTTP 상태)이 깨져 전부 500으로 뭉개진다 - SpeechStreamPipeline.await()의
     * ExecutionException 언래핑과 같은 이유.
     */
    private static <T> T join(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw e;
        }
    }

    /** STT + AI 응답까지 끝낸 한 턴의 텍스트 결과 (일괄 처리용) */
    private record ResolvedTurn(UserContext context, String userMessage, String aiResponse, boolean sttEmpty) {
    }

    public ConversationResponse processUserMessage(Long userId, MultipartFile audioFile) {
        long turnStart = System.currentTimeMillis();
        ResolvedTurn turn = resolveTurn(userId, audioFile);
        UserContext context = turn.context();

        // 4. TTS 변환
        byte[] audioData = timed("tts", userId, context.getConversationHistory().size(),
                () -> voiceService.textToSpeech(turn.aiResponse(), context.getPreferences().getVoiceSettings()));

        // 5. 히스토리 업데이트 (동기)
        addTurnToHistory(userId, turn);

        recordTotal("message_total", userId, turnStart, context.getConversationHistory().size());

        return ConversationResponse.builder()
                .userMessage(turn.userMessage())
                .aiResponse(turn.aiResponse())
                .audioData(audioData)
                .timestamp(LocalDateTime.now())
                .build();
    }

    /**
     * processUserMessage의 스트리밍 버전 - AI 응답을 LLM이 생성하는 대로 조각 단위로 TTS해, 첫 조각의 오디오 첫 바이트가
     * 도착하면 바로 반환한다. 나머지 조각과 히스토리 저장은 {@link SpeechStreamPipeline}이 이어서 처리한다
     * (히스토리 규칙은 그 클래스 주석 참고).
     *
     * STT 결과가 비어 있으면 LLM 없이 고정 안내 문구 한 조각으로 응답한다.
     */
    public StreamedConversation processUserMessageStream(Long userId, MultipartFile audioFile) {
        long turnStart = System.currentTimeMillis();
        TranscribedTurn turn = transcribe(userId, audioFile);
        UserContext context = turn.context();
        int historyTurns = context.getConversationHistory().size();
        VoiceSettings voiceSettings = context.getPreferences().getVoiceSettings();

        if (turn.sttEmpty()) {
            return emptySttStream(userId, turn, voiceSettings, turnStart, historyTurns);
        }

        context.setConsecutiveEmptySttCount(0);
        String userMessage = turn.userMessage();
        // 풀 스레드에서 읽으므로, 이번 턴 저장 전의 히스토리를 고정해서 넘긴다
        List<ConversationTurn> history = List.copyOf(context.getConversationHistory());
        SpeechStreamPipeline.Started started = speechStreamPipeline.start(
                () -> aiService.openResponseStream(context.getSystemPrompt(), history, userMessage),
                voiceSettings,
                MESSAGE_STAGES,
                (stage, elapsedMs) -> recordElapsed(stage, userId, elapsedMs, historyTurns),
                aiResponse -> contextService.addConversationTurn(userId, userMessage, aiResponse));

        recordTotal("message_first_audio", userId, turnStart, historyTurns);

        return new StreamedConversation(
                userMessage,
                started.segments(),
                () -> recordTotal("tts_stream_total", userId, started.ttsStartedAtMs(), historyTurns)
        );
    }

    /** STT가 비었을 때 - LLM 없이 고정 안내 문구를 한 조각으로 보낸다. 히스토리는 TTS를 연 뒤 저장한다. */
    private StreamedConversation emptySttStream(Long userId, TranscribedTurn turn, VoiceSettings voiceSettings,
                                                long turnStart, int historyTurns) {
        String aiResponse = emptySttResponse(userId, turn.context());
        long ttsStart = System.currentTimeMillis();
        InputStream audioStream = timed("tts_first_byte", userId, historyTurns,
                () -> voiceService.textToSpeechStream(aiResponse, voiceSettings));

        // 빈 user 메시지는 기록하지 않는다(addTurnToHistory 참고). 실패하면 열어 둔 TTS 연결을 반드시 닫는다
        try {
            contextService.addConversationTurn(userId, null, aiResponse);
        } catch (RuntimeException e) {
            closeQuietly(audioStream);
            throw e;
        }

        recordTotal("message_first_audio", userId, turnStart, historyTurns);

        return new StreamedConversation(
                turn.userMessage(),
                SpeechSegmentSource.single(new SpeechSegment(aiResponse, audioStream)),
                () -> recordTotal("tts_stream_total", userId, ttsStart, historyTurns)
        );
    }

    /** STT까지 끝낸 한 턴 (일괄/스트리밍 공통 구간) */
    private record TranscribedTurn(UserContext context, String userMessage, boolean sttEmpty) {
    }

    /** 컨텍스트 조회 → STT (일괄/스트리밍 공통 구간) */
    private TranscribedTurn transcribe(Long userId, MultipartFile audioFile) {
        // 1. 컨텍스트 조회
        UserContext context = contextService.getContext(userId);

        // 2. STT 변환
        String userMessage = timed("stt", userId, context.getConversationHistory().size(),
                () -> voiceService.speechToText(audioFile));
        return new TranscribedTurn(context, userMessage, userMessage.isBlank());
    }

    /**
     * STT 결과가 비어있으면(무음 등) AI를 호출하지 않고 바로 재요청 안내로 응답한다.
     * 연속 횟수에 따라 문구를 단계적으로 바꾸고, 알아들을 수 있는 발화가 들어오면 리셋한다.
     */
    private String emptySttResponse(Long userId, UserContext context) {
        context.setConsecutiveEmptySttCount(context.getConsecutiveEmptySttCount() + 1);
        log.info("STT 결과가 비어있어 AI 호출 없이 안내로 응답 - userId: {}, 연속 {}회",
                userId, context.getConsecutiveEmptySttCount());
        return emptySttFallbackResponse(context.getConsecutiveEmptySttCount());
    }

    /** 컨텍스트 조회 → STT → AI 응답 생성 (일괄 처리용) */
    private ResolvedTurn resolveTurn(Long userId, MultipartFile audioFile) {
        TranscribedTurn transcribed = transcribe(userId, audioFile);
        UserContext context = transcribed.context();
        String userMessage = transcribed.userMessage();

        // 3. AI 응답 생성 (OpenAI 권장 방식: messages 배열)
        String aiResponse;
        if (transcribed.sttEmpty()) {
            aiResponse = emptySttResponse(userId, context);
        } else {
            context.setConsecutiveEmptySttCount(0);
            String systemPrompt = context.getSystemPrompt();
            List<ConversationTurn> history = context.getConversationHistory();
            aiResponse = timed("llm", userId, history.size(),
                    () -> aiService.generateResponse(systemPrompt, history, userMessage));
        }

        return new ResolvedTurn(context, userMessage, aiResponse, transcribed.sttEmpty());
    }

    /**
     * 히스토리 업데이트.
     * 빈 user 메시지는 기록하지 않는다(null이면 첫 인사 턴처럼 AI 발화만 기록됨) -
     * 이후 턴에서 이 히스토리가 다시 messages 배열에 실릴 때 빈 user 메시지가 섞이지 않도록.
     */
    private void addTurnToHistory(Long userId, ResolvedTurn turn) {
        contextService.addConversationTurn(
                userId, turn.sttEmpty() ? null : turn.userMessage(), turn.aiResponse());
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // 이미 실패 경로에서 정리 중이므로 무시
        }
    }

    /**
     * 최근 7일의 성공한 일기 조회
     *
     * 일기 조회에 실패해도 대화 시작을 막지 않음 (일기 없이 진행)
     */
    private List<Diary> loadRecentDiaries(Long userId) {
        try {
            List<Diary> recentDiaries = diaryService.getRecentSuccessfulDiaries(userId, RECENT_DIARY_DAYS);
            log.info("최근 일기 {}건을 시스템 프롬프트에 주입 - userId: {}", recentDiaries.size(), userId);
            return recentDiaries;
        } catch (Exception e) {
            log.warn("최근 일기 조회 실패 - 일기 없이 대화 시작 - userId: {}", userId, e);
            return List.of();
        }
    }

    /** 조회해온 최근 일기를 시스템 프롬프트 뒤에 덧붙임 (일기가 없으면 원본 그대로 반환) */
    private String appendRecentDiaries(String systemPrompt, List<Diary> recentDiaries) {
        if (recentDiaries.isEmpty()) {
            return systemPrompt;
        }

        StringBuilder sb = new StringBuilder(systemPrompt);
        sb.append("\n\n────────────────────────────────────────\n");
        sb.append("[최근 7일의 일기 - 이전 대화에서 나온 이야기입니다. ");
        sb.append("자연스럽게 이어가되, 같은 질문을 반복하지 마세요]\n");
        recentDiaries.forEach(diary -> sb.append("- ")
                .append(diary.getDiaryDate().getMonthValue()).append("월 ")
                .append(diary.getDiaryDate().getDayOfMonth()).append("일: ")
                .append(diary.getContent().replace("\n", " "))
                .append("\n"));
        return sb.toString();
    }

    /**
     * 시스템 프롬프트에 주입할 장기기억 조회
     *
     * 일기(최근 7일)가 "오늘 무슨 일이 있었나"라면, 장기기억은 "이 분은 어떤 분인가"에 해당한다.
     * 오늘의 방문 장소를 단서 삼아 옛 기억을 끌어내는 회상 대화의 재료로 쓰인다.
     *
     * 기억 조회에 실패해도 대화 시작을 막지 않음 (기억 없이 진행)
     *
     * 기억이 상한(20개)을 넘어 선별이 필요해지면 이 메서드 안에서만 교체하면 된다.
     */
    private List<Memory> loadLifeMemories(Long userId) {
        try {
            List<Memory> memories = memoryService.getMemories(userId);
            log.info("장기기억 {}건을 시스템 프롬프트에 주입 - userId: {}", memories.size(), userId);
            return memories;
        } catch (Exception e) {
            log.warn("장기기억 조회 실패 - 장기기억 없이 대화 시작 - userId: {}", userId, e);
            return List.of();
        }
    }

    /**
     * 비동기 기억 추출에 넘길 컨텍스트 스냅샷
     *
     * finalizeContext()가 원본을 contextStore에서 제거하고, 같은 userId로 새 대화가 시작되면
     * 새 UserContext가 만들어진다. UserContext는 가변(@Data)이므로 추출에 실제로 쓰이는 값만
     * 불변으로 복사해 백그라운드 작업을 세션 수명과 분리한다.
     */
    private UserContext snapshotForMemoryExtraction(UserContext context) {
        return UserContext.builder()
                .userId(context.getUserId())
                .startedAt(context.getStartedAt())
                .preferences(context.getPreferences())
                .conversationHistory(List.copyOf(context.getConversationHistory()))
                .build();
    }

    public TtsRetryResponse retryTts(Long userId) {
        UserContext context = contextService.getContext(userId);

        List<ConversationTurn> history = context.getConversationHistory();
        if (history.isEmpty()) {
            throw new ConversationNotFoundException("재시도할 대화 기록이 없습니다.");
        }

        String lastAiResponse = history.get(history.size() - 1).getAiResponse();
        byte[] audioData = timed("tts_retry", userId, history.size(),
                () -> voiceService.textToSpeech(lastAiResponse, context.getPreferences().getVoiceSettings()));

        return TtsRetryResponse.builder()
                .aiResponse(lastAiResponse)
                .audioData(audioData)
                .build();
    }

    public ConversationEndResponse endConversation(Long userId) {
        log.info("대화 종료 시작 - userId: {}", userId);

        String diaryStatus;
        Long diaryId = null;
        String diaryError = null;
        LocalDate diaryDate = null;

        try {
            // 1. 컨텍스트 조회
            UserContext context = contextService.getContext(userId);
            log.info("컨텍스트 조회 완료 - 대화 턴 수: {}", context.getConversationHistory().size());

            // 2. 일기 생성 (동기) - 실패해도 대화 종료는 계속되며, 결과를 응답에 명시
            DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);
            if (outcome instanceof DiaryOutcome.Processed processed) {
                Diary diary = processed.diary();
                diaryStatus = diary.getStatus().name();
                diaryId = diary.getId();
                diaryError = diary.getFailureReason();
                // 클라이언트가 이 세션을 일기 탭에서 로컬 타임스탬프 추정 대신 신뢰할 수 있도록 전달
                diaryDate = diary.getDiaryDate();
            } else {
                diaryStatus = "SKIPPED";
            }

            // 3. 장기기억 추출 (비동기) - 응답에 실리지 않으므로 사용자를 기다리게 하지 않음
            //    대화 원문은 아래 finalizeContext에서 사라지므로, 넘기기 전에 스냅샷을 뜸
            UserContext snapshot = snapshotForMemoryExtraction(context);
            taskExecutor.execute(() -> {
                try {
                    memoryService.extractAndSaveMemories(snapshot);
                } catch (Exception e) {
                    log.warn("장기기억 추출 실패 - userId: {}", userId, e);
                }
            });
        } catch (Exception e) {
            log.error("일기 생성 실패 - userId: {}", userId, e);
            diaryStatus = "FAILED";
            diaryError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        } finally {
            // 4. 컨텍스트 정리 (어떤 경우에도 보장)
            contextService.finalizeContext(userId);
        }

        log.info("=== 대화 종료 완료 - userId: {}, diaryStatus: {} ===", userId, diaryStatus);

        return ConversationEndResponse.builder()
                .endedAt(LocalDateTime.now())
                .diaryStatus(diaryStatus)
                .diaryId(diaryId)
                .diaryError(diaryError)
                .diaryDate(diaryDate)
                .build();
    }
}
