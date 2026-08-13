package com.example.echo.diary.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.diary.entity.Diary;
import com.example.echo.diary.entity.DiaryStatus;
import com.example.echo.diary.exception.DiaryNotFoundException;
import com.example.echo.diary.exception.InvalidDateRangeException;
import com.example.echo.diary.repository.DiaryRepository;
import com.example.echo.prompt.service.PromptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 일기 서비스
 *
 * 대화 종료 시 하루 1개 일기를 생성/갱신
 * - 대화 원문은 서버에 영구 저장되지 않으므로(클라이언트 로컬에만 존재),
 *   [기존 저장된 오늘 일기 + 이번 세션 대화]를 입력으로 증분 재생성
 * - 생성 실패 시에도 FAILED 레코드를 남겨 클라이언트에서 확인 가능 (디버깅 목적)
 * - 날짜 경계는 Asia/Seoul 기준 (배포 환경 JVM 타임존에 의존하지 않음)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int FAILURE_REASON_MAX_LENGTH = 500;
    private static final DateTimeFormatter TITLE_FORMATTER = DateTimeFormatter.ofPattern("M월 d일의 일기");

    private final DiaryRepository diaryRepository;
    private final PromptService promptService;
    private final AIService aiService;

    /**
     * 대화 종료 시 오늘의 일기 생성/갱신
     *
     * @param context 대화 종료 시점의 UserContext
     * @return Skipped(사용자 발화 없어 의도적으로 건너뜀) 또는
     *         Processed(생성 시도함 - diary.status가 SUCCESS/FAILED, 실패 기록 저장까지 실패하면 diary.id는 null)
     */
    @Transactional
    public DiaryOutcome generateAndSaveDiary(UserContext context) {
        Long userId = context.getUserId();
        LocalDate today = LocalDate.now(KST);

        log.info("일기 생성 시작 - userId: {}, date: {}, 대화 턴 수: {}",
                userId, today, context.getConversationHistory().size());

        // 인사만 듣고 종료한 세션은 일기를 만들 내용이 없음 - 기존 일기를 건드리지 않고 스킵
        if (!hasUserMessage(context.getConversationHistory())) {
            log.info("일기 생성 스킵 - 사용자 발화 없음 - userId: {}", userId);
            return new DiaryOutcome.Skipped();
        }

        Diary existing = diaryRepository.findByUserIdAndDiaryDate(userId, today).orElse(null);

        try {
            String diaryPrompt = promptService.buildDiaryPrompt(
                    context, existing != null ? existing.getContent() : null);
            String content = aiService.generateDiary(diaryPrompt, context.getSessionModel());
            String weather = extractWeather(context);

            Diary saved = upsertSuccess(userId, today, existing, content, weather);
            log.info("일기 생성 완료 - userId: {}, diaryId: {}, 길이: {}", userId, saved.getId(), content.length());
            return new DiaryOutcome.Processed(saved);
        } catch (Exception e) {
            log.error("일기 생성 실패 - userId: {}, date: {}", userId, today, e);
            return new DiaryOutcome.Processed(saveFailure(userId, today, existing, e));
        }
    }

    /**
     * 최근 N일 일기 조회 (오늘 포함, 날짜 내림차순)
     */
    @Transactional(readOnly = true)
    public List<Diary> getDiaries(Long userId, int days) {
        LocalDate today = LocalDate.now(KST);
        return getDiariesInRange(userId, today.minusDays(days - 1L), today);
    }

    /**
     * 지정한 날짜 범위(양끝 포함)의 일기 조회 (날짜 내림차순)
     *
     * 캘린더 월 이동 등 임의 구간 조회에 사용 (일기 탭 캘린더 UI)
     */
    @Transactional(readOnly = true)
    public List<Diary> getDiariesInRange(Long userId, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidDateRangeException(startDate, endDate);
        }
        return diaryRepository.findByUserIdAndDiaryDateBetweenOrderByDiaryDateDesc(
                userId, startDate, endDate);
    }

    /**
     * 대화 컨텍스트 주입용: 최근 N일 중 생성에 성공한 일기만 조회 (날짜 내림차순)
     */
    @Transactional(readOnly = true)
    public List<Diary> getRecentSuccessfulDiaries(Long userId, int days) {
        return getDiaries(userId, days).stream()
                .filter(diary -> diary.getStatus() == DiaryStatus.SUCCESS
                        && diary.getContent() != null && !diary.getContent().isBlank())
                .toList();
    }

    /**
     * 일기 단건 조회 (본인 소유만)
     */
    @Transactional(readOnly = true)
    public Diary getDiary(Long diaryId, Long userId) {
        return diaryRepository.findByIdAndUserId(diaryId, userId)
                .orElseThrow(() -> new DiaryNotFoundException(diaryId));
    }

    private boolean hasUserMessage(List<ConversationTurn> history) {
        return history != null && history.stream().anyMatch(turn -> turn.getUserMessage() != null);
    }

    private Diary upsertSuccess(Long userId, LocalDate date, Diary existing, String content, String weather) {
        if (existing != null) {
            existing.markSuccess(content, weather);
            return diaryRepository.save(existing);
        }
        try {
            return diaryRepository.save(buildNewDiary(userId, date, content, weather, DiaryStatus.SUCCESS, null));
        } catch (DataIntegrityViolationException e) {
            // unique(user_id, diary_date) 경합 시 재조회 후 1회 갱신 재시도
            log.warn("일기 저장 경합 감지 - 재조회 후 갱신 - userId: {}, date: {}", userId, date);
            Diary raced = diaryRepository.findByUserIdAndDiaryDate(userId, date)
                    .orElseThrow(() -> e);
            raced.markSuccess(content, weather);
            return diaryRepository.save(raced);
        }
    }

    private Diary saveFailure(Long userId, LocalDate date, Diary existing, Exception cause) {
        String reason = truncate(cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName());
        Diary failed;
        if (existing != null) {
            // 같은 날 이미 성공한 일기가 있으면 content는 보존하고 상태만 FAILED로 표시
            existing.markFailed(reason);
            failed = existing;
        } else {
            failed = buildNewDiary(userId, date, null, null, DiaryStatus.FAILED, reason);
        }
        try {
            return diaryRepository.save(failed);
        } catch (Exception saveError) {
            // 실패 기록 저장마저 실패해도 대화 종료 흐름은 막지 않음.
            // DiaryOutcome.Processed로 감싸 반환해야 하므로, 저장은 못 했더라도(id=null)
            // status=FAILED인 객체는 그대로 반환한다
            log.error("일기 실패 기록 저장 실패 - userId: {}, date: {}", userId, date, saveError);
            return failed;
        }
    }

    private Diary buildNewDiary(Long userId, LocalDate date, String content, String weather,
                                DiaryStatus status, String failureReason) {
        return Diary.builder()
                .userId(userId)
                .diaryDate(date)
                .title(date.format(TITLE_FORMATTER))
                .content(content)
                .weather(weather)
                .status(status)
                .failureReason(failureReason)
                .build();
    }

    private String extractWeather(UserContext context) {
        if (context.getTodayWeather() == null) {
            return null;
        }
        return context.getTodayWeather().getDescription();
    }

    private String truncate(String text) {
        if (text == null || text.length() <= FAILURE_REASON_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
