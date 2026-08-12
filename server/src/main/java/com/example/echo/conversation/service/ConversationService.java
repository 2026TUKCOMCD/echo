package com.example.echo.conversation.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.UserContext;
import com.example.echo.context.service.ContextService;
import com.example.echo.conversation.dto.ConversationEndResponse;
import com.example.echo.conversation.dto.ConversationResponse;
import com.example.echo.conversation.dto.ConversationStartResponse;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final VoiceService voiceService;
    private final PromptService promptService;
    private final AIService aiService;
    private final ContextService contextService;
    private final DiaryService diaryService;
    private final HealthDataService healthDataService;
    private final MemoryService memoryService;
    private final RecallTopicRotationService recallTopicRotationService;

    public ConversationStartResponse startConversation(Long userId, HealthData healthData, RawLocationData rawLocationData) {
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
        String firstMessage = aiService.generateGreeting(systemPrompt, context);

        // 5. TTS 변환
        byte[] audioData = voiceService.textToSpeech(firstMessage, context.getPreferences().getVoiceSettings());

        // 6. 히스토리 추가 (동기 - tts-retry에서 히스토리 조회 보장)
        contextService.addConversationTurn(userId, null, firstMessage);

        return ConversationStartResponse.builder()
                .message(firstMessage)
                .audioData(audioData)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public ConversationResponse processUserMessage(Long userId, MultipartFile audioFile) {
        // 1. 컨텍스트 조회
        UserContext context = contextService.getContext(userId);

        // 2. STT 변환
        String userMessage = voiceService.speechToText(audioFile);

        // 3. AI 응답 생성 (OpenAI 권장 방식: messages 배열)
        String systemPrompt = context.getSystemPrompt();
        List<ConversationTurn> history = context.getConversationHistory();
        String aiResponse = aiService.generateResponse(systemPrompt, history, userMessage);

        // 4. TTS 변환
        byte[] audioData = voiceService.textToSpeech(aiResponse, context.getPreferences().getVoiceSettings());

        // 5. 히스토리 업데이트 (동기)
        contextService.addConversationTurn(userId, userMessage, aiResponse);

        return ConversationResponse.builder()
                .userMessage(userMessage)
                .aiResponse(aiResponse)
                .audioData(audioData)
                .timestamp(LocalDateTime.now())
                .build();
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

    public TtsRetryResponse retryTts(Long userId) {
        UserContext context = contextService.getContext(userId);

        List<ConversationTurn> history = context.getConversationHistory();
        if (history.isEmpty()) {
            throw new ConversationNotFoundException("재시도할 대화 기록이 없습니다.");
        }

        String lastAiResponse = history.get(history.size() - 1).getAiResponse();
        byte[] audioData = voiceService.textToSpeech(lastAiResponse, context.getPreferences().getVoiceSettings());

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

            // 3. 장기기억 추출 (동기) - 일기와 독립적이며, 실패해도 대화 종료·일기 결과에 영향 없음
            //    대화 원문은 아래 finalizeContext에서 사라지므로 반드시 그 전에 추출해야 함
            try {
                memoryService.extractAndSaveMemories(context);
            } catch (Exception e) {
                log.warn("장기기억 추출 실패 - userId: {}", userId, e);
            }
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
