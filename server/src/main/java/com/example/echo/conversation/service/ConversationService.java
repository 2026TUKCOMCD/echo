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

    public ConversationStartResponse startConversation(Long userId, HealthData healthData, RawLocationData rawLocationData) {
        long turnStart = System.currentTimeMillis();
        // 0. 건강 데이터 저장 (Android에서 수신한 경우)
        if (healthData != null) {
            healthDataService.saveHealthData(userId, healthData);
        }

        // 1. 컨텍스트 초기화 (healthData, locationData 전달)
        UserContext context = contextService.initializeContext(userId, healthData, rawLocationData);

        // 2. 오늘의 장기기억 회상 주제(3단계용) 확정
        // 시스템 프롬프트는 대화 시작 시 1회 생성되어 세션 내내 재사용되므로, 여기서 확정한 주제가
        // 자정을 넘겨도 세션 중에는 그대로 유지된다. 시스템 프롬프트에 구워 넣어야 하니 2보다 먼저 계산한다.
        List<Memory> lifeMemories = loadLifeMemories(userId);
        String recallTopic = recallTopicRotationService.currentTopic();
        String recallGuide = promptService.buildRecallGuide(recallTopic, lifeMemories);

        // 3. 시스템 프롬프트 생성 및 컨텍스트에 캐싱 (processUserMessage에서 재사용)
        // 장기기억·오늘의 회상 주제는 템플릿 변수로, 최근 7일 일기는 뒤에 덧붙여
        // AI가 이전 대화를 기억하는 것처럼 이어가게 함
        String systemPrompt = appendRecentDiaries(
                promptService.buildSystemPrompt(context, lifeMemories, recallGuide), userId);
        context.setSystemPrompt(systemPrompt);

        // 4. 첫 인사 생성
        String firstMessage = timed("llm_greeting", userId, context.getConversationHistory().size(),
                () -> aiService.generateGreeting(systemPrompt, context));

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

    /** STT + AI 응답까지 끝낸 한 턴의 텍스트 결과. TTS는 호출 방식(일괄/스트리밍)에 따라 이후 단계에서 처리한다. */
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
     * processUserMessage의 스트리밍 버전 - TTS 전체 합성을 기다리지 않고, 첫 오디오 바이트가 도착하면
     * 텍스트와 함께 스트림을 반환한다. 히스토리는 스트림이 열린 뒤에 저장하므로 TTS를 열지 못한 턴은
     * 기존 /message와 같이 기록되지 않고 예외로 끝난다. 이후 스트림이 중간에 끊겨도 히스토리는
     * 저장돼 있어 tts-retry가 마지막 응답을 정상적으로 찾을 수 있다.
     */
    public StreamedConversation processUserMessageStream(Long userId, MultipartFile audioFile) {
        long turnStart = System.currentTimeMillis();
        ResolvedTurn turn = resolveTurn(userId, audioFile);
        UserContext context = turn.context();
        int historyTurns = context.getConversationHistory().size();
        long ttsStart = System.currentTimeMillis();

        // 4. TTS 스트림 열기 - 첫 오디오 바이트가 도착할 때까지 대기한다 (tts_first_byte = 체감 지연)
        InputStream audioStream = timed("tts_first_byte", userId, historyTurns,
                () -> voiceService.textToSpeechStream(turn.aiResponse(), context.getPreferences().getVoiceSettings()));

        // 5. 히스토리 업데이트 (동기) - 실패하면 열어 둔 TTS 연결을 반드시 닫는다
        try {
            addTurnToHistory(userId, turn);
        } catch (RuntimeException e) {
            closeQuietly(audioStream);
            throw e;
        }

        recordTotal("message_first_audio", userId, turnStart, context.getConversationHistory().size());

        return new StreamedConversation(
                turn.userMessage(),
                turn.aiResponse(),
                audioStream,
                () -> recordTotal("tts_stream_total", userId, ttsStart, historyTurns)
        );
    }

    /** 컨텍스트 조회 → STT → AI 응답 생성 (일괄/스트리밍 공통 구간) */
    private ResolvedTurn resolveTurn(Long userId, MultipartFile audioFile) {
        // 1. 컨텍스트 조회
        UserContext context = contextService.getContext(userId);

        // 2. STT 변환
        String userMessage = timed("stt", userId, context.getConversationHistory().size(),
                () -> voiceService.speechToText(audioFile));
        boolean sttEmpty = userMessage.isBlank();

        // 3. AI 응답 생성 (OpenAI 권장 방식: messages 배열)
        //    STT 결과가 비어있으면(무음 등) AI를 호출하지 않고 바로 재요청 안내로 응답한다.
        //    연속 횟수에 따라 문구를 단계적으로 바꾸고, 알아들을 수 있는 발화가 들어오면 리셋한다.
        String aiResponse;
        if (sttEmpty) {
            context.setConsecutiveEmptySttCount(context.getConsecutiveEmptySttCount() + 1);
            log.info("STT 결과가 비어있어 AI 호출 없이 안내로 응답 - userId: {}, 연속 {}회",
                    userId, context.getConsecutiveEmptySttCount());
            aiResponse = emptySttFallbackResponse(context.getConsecutiveEmptySttCount());
        } else {
            context.setConsecutiveEmptySttCount(0);
            String systemPrompt = context.getSystemPrompt();
            List<ConversationTurn> history = context.getConversationHistory();
            aiResponse = timed("llm", userId, history.size(),
                    () -> aiService.generateResponse(systemPrompt, history, userMessage));
        }

        return new ResolvedTurn(context, userMessage, aiResponse, sttEmpty);
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
     * 최근 7일의 일기를 시스템 프롬프트에 덧붙임
     *
     * 일기 조회에 실패해도 대화 시작을 막지 않음 (원본 프롬프트 그대로 반환)
     */
    private String appendRecentDiaries(String systemPrompt, Long userId) {
        try {
            List<Diary> recentDiaries = diaryService.getRecentSuccessfulDiaries(userId, 7);
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

            log.info("최근 일기 {}건을 시스템 프롬프트에 주입 - userId: {}", recentDiaries.size(), userId);
            return sb.toString();
        } catch (Exception e) {
            log.warn("최근 일기 조회 실패 - 일기 없이 대화 시작 - userId: {}", userId, e);
            return systemPrompt;
        }
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
