package com.example.echo.diary.service;

import com.example.echo.ai.exception.AIException;
import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.diary.entity.Diary;
import com.example.echo.diary.entity.DiaryStatus;
import com.example.echo.diary.exception.InvalidDateRangeException;
import com.example.echo.diary.repository.DiaryRepository;
import com.example.echo.prompt.service.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    private static final Long TEST_USER_ID = 1L;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private PromptService promptService;

    @Mock
    private AIService aiService;

    private DiaryService diaryService;

    @BeforeEach
    void setUp() {
        diaryService = new DiaryService(diaryRepository, promptService, aiService);
    }

    private UserContext contextWithUserMessage() {
        UserContext context = UserContext.builder()
                .userId(TEST_USER_ID)
                .conversationHistory(new CopyOnWriteArrayList<>())
                .build();
        // 첫 인사 턴 (userMessage 없음)
        context.getConversationHistory().add(ConversationTurn.builder()
                .aiResponse("안녕하세요!")
                .timestamp(LocalDateTime.now())
                .build());
        // 실제 대화 턴
        context.getConversationHistory().add(ConversationTurn.builder()
                .userMessage("오늘 산책 다녀왔어요")
                .aiResponse("좋으셨겠어요!")
                .timestamp(LocalDateTime.now())
                .build());
        return context;
    }

    @Test
    @DisplayName("오늘 일기가 없으면 새 일기를 SUCCESS로 저장한다")
    void generateAndSaveDiary_createsNewDiary() {
        // given
        UserContext context = contextWithUserMessage();
        when(diaryRepository.findByUserIdAndDiaryDate(TEST_USER_ID, TODAY)).thenReturn(Optional.empty());
        when(promptService.buildDiaryPrompt(eq(context), isNull())).thenReturn("일기 프롬프트");
        when(aiService.generateDiary(eq("일기 프롬프트"), any())).thenReturn("오늘은 산책을 다녀왔다.");
        when(diaryRepository.save(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);

        // then
        Diary result = processedDiary(outcome);
        assertThat(result.getStatus()).isEqualTo(DiaryStatus.SUCCESS);
        assertThat(result.getContent()).isEqualTo("오늘은 산책을 다녀왔다.");
        assertThat(result.getDiaryDate()).isEqualTo(TODAY);
        assertThat(result.getTitle()).contains("일기");
    }

    @Test
    @DisplayName("오늘 일기가 이미 있으면 기존 내용을 프롬프트에 포함해 증분 갱신한다")
    void generateAndSaveDiary_updatesExistingDiaryIncrementally() {
        // given
        UserContext context = contextWithUserMessage();
        Diary existing = Diary.builder()
                .userId(TEST_USER_ID)
                .diaryDate(TODAY)
                .title("기존 제목")
                .content("아침에 쓴 기존 일기")
                .status(DiaryStatus.SUCCESS)
                .build();
        when(diaryRepository.findByUserIdAndDiaryDate(TEST_USER_ID, TODAY)).thenReturn(Optional.of(existing));
        when(promptService.buildDiaryPrompt(eq(context), eq("아침에 쓴 기존 일기"))).thenReturn("증분 프롬프트");
        when(aiService.generateDiary(eq("증분 프롬프트"), any())).thenReturn("아침 산책과 오후 이야기를 담은 통합 일기");
        when(diaryRepository.save(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);

        // then
        verify(promptService).buildDiaryPrompt(eq(context), eq("아침에 쓴 기존 일기"));
        Diary result = processedDiary(outcome);
        assertThat(result.getStatus()).isEqualTo(DiaryStatus.SUCCESS);
        assertThat(result.getContent()).isEqualTo("아침 산책과 오후 이야기를 담은 통합 일기");
        assertThat(result.getFailureReason()).isNull();
    }

    @Test
    @DisplayName("AI 생성 실패 시 기존 content를 보존한 채 FAILED로 기록하고 예외를 던지지 않는다")
    void generateAndSaveDiary_marksFailedAndPreservesContent() {
        // given
        UserContext context = contextWithUserMessage();
        Diary existing = Diary.builder()
                .userId(TEST_USER_ID)
                .diaryDate(TODAY)
                .content("아침에 성공한 일기")
                .status(DiaryStatus.SUCCESS)
                .build();
        when(diaryRepository.findByUserIdAndDiaryDate(TEST_USER_ID, TODAY)).thenReturn(Optional.of(existing));
        when(promptService.buildDiaryPrompt(any(), anyString())).thenReturn("프롬프트");
        when(aiService.generateDiary(anyString(), any())).thenThrow(new AIException("AI 일기 생성 실패: 401"));
        when(diaryRepository.save(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);

        // then
        Diary result = processedDiary(outcome);
        assertThat(result.getStatus()).isEqualTo(DiaryStatus.FAILED);
        assertThat(result.getFailureReason()).contains("AI 일기 생성 실패");
        assertThat(result.getContent()).isEqualTo("아침에 성공한 일기"); // 성공본 보존
    }

    @Test
    @DisplayName("AI 생성과 실패 기록 저장이 모두 실패해도 Skipped와 구분되는 Processed(FAILED)를 반환한다")
    void generateAndSaveDiary_returnsProcessedFailedEvenWhenFailureRecordSaveFails() {
        // given
        UserContext context = contextWithUserMessage();
        when(diaryRepository.findByUserIdAndDiaryDate(TEST_USER_ID, TODAY)).thenReturn(Optional.empty());
        when(promptService.buildDiaryPrompt(eq(context), isNull())).thenReturn("일기 프롬프트");
        when(aiService.generateDiary(eq("일기 프롬프트"), any())).thenThrow(new AIException("AI 일기 생성 실패: 500"));
        when(diaryRepository.save(any(Diary.class))).thenThrow(new RuntimeException("DB 연결 끊김"));

        // when
        DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);

        // then: Skipped가 아니라 Processed(FAILED)여야 "발화 없어 스킵"과 구분됨
        assertThat(outcome).isInstanceOf(DiaryOutcome.Processed.class);
        Diary result = processedDiary(outcome);
        assertThat(result.getStatus()).isEqualTo(DiaryStatus.FAILED);
        assertThat(result.getFailureReason()).contains("AI 일기 생성 실패");
        assertThat(result.getId()).isNull(); // 저장 자체는 실패했으므로 id는 없음
    }

    @Test
    @DisplayName("사용자 발화가 없는 세션(인사만 듣고 종료)은 일기를 생성하지 않고 스킵한다")
    void generateAndSaveDiary_skipsWhenNoUserMessage() {
        // given
        UserContext context = UserContext.builder()
                .userId(TEST_USER_ID)
                .conversationHistory(new CopyOnWriteArrayList<>())
                .build();
        context.getConversationHistory().add(ConversationTurn.builder()
                .aiResponse("안녕하세요!")
                .timestamp(LocalDateTime.now())
                .build());

        // when
        DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);

        // then
        assertThat(outcome).isInstanceOf(DiaryOutcome.Skipped.class);
        verifyNoInteractions(aiService);
        verify(diaryRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 저장 시 경합이 발생하면 재조회 후 갱신하여 SUCCESS로 저장한다")
    void generateAndSaveDiary_retriesAndSucceedsOnConcurrentInsert() {
        // given
        UserContext context = contextWithUserMessage();
        Diary racedDiary = Diary.builder()
                .userId(TEST_USER_ID)
                .diaryDate(TODAY)
                .title("경합 상대가 만든 일기")
                .content("경합 상대가 저장한 내용")
                .status(DiaryStatus.SUCCESS)
                .build();
        when(diaryRepository.findByUserIdAndDiaryDate(TEST_USER_ID, TODAY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(racedDiary));
        when(promptService.buildDiaryPrompt(eq(context), isNull())).thenReturn("일기 프롬프트");
        when(aiService.generateDiary(eq("일기 프롬프트"), any())).thenReturn("재시도로 저장된 새 내용");
        when(diaryRepository.save(any(Diary.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violated"))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);

        // then
        Diary result = processedDiary(outcome);
        assertThat(result.getStatus()).isEqualTo(DiaryStatus.SUCCESS);
        assertThat(result.getContent()).isEqualTo("재시도로 저장된 새 내용");
        verify(diaryRepository, times(2)).findByUserIdAndDiaryDate(TEST_USER_ID, TODAY);
        verify(diaryRepository, times(2)).save(any(Diary.class));
    }

    @Test
    @DisplayName("경합 재시도 조회마저 실패하면 원래 예외가 전파되어 FAILED로 기록된다")
    void generateAndSaveDiary_marksFailedWhenRetryAlsoFindsNothing() {
        // given
        UserContext context = contextWithUserMessage();
        when(diaryRepository.findByUserIdAndDiaryDate(TEST_USER_ID, TODAY))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(promptService.buildDiaryPrompt(eq(context), isNull())).thenReturn("일기 프롬프트");
        when(aiService.generateDiary(eq("일기 프롬프트"), any())).thenReturn("생성된 내용");
        when(diaryRepository.save(any(Diary.class)))
                .thenThrow(new DataIntegrityViolationException("unique constraint violated"))
                .thenAnswer(inv -> inv.getArgument(0));

        // when
        DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);

        // then
        Diary result = processedDiary(outcome);
        assertThat(result.getStatus()).isEqualTo(DiaryStatus.FAILED);
        assertThat(result.getFailureReason()).contains("unique constraint violated");
        verify(diaryRepository, times(2)).findByUserIdAndDiaryDate(TEST_USER_ID, TODAY);
        verify(aiService, times(1)).generateDiary(anyString(), any());
    }

    @Test
    @DisplayName("AI 생성 실패 후 실패 기록 저장마저 실패하면 id가 null인 FAILED 레코드를 그대로 반환한다")
    void generateAndSaveDiary_returnsUnsavedFailedDiaryWhenFailureRecordSaveAlsoFails() {
        // given
        UserContext context = contextWithUserMessage();
        when(diaryRepository.findByUserIdAndDiaryDate(TEST_USER_ID, TODAY)).thenReturn(Optional.empty());
        when(promptService.buildDiaryPrompt(eq(context), isNull())).thenReturn("일기 프롬프트");
        when(aiService.generateDiary(eq("일기 프롬프트"), any())).thenThrow(new AIException("AI 일기 생성 실패: 401"));
        when(diaryRepository.save(any(Diary.class)))
                .thenThrow(new DataAccessResourceFailureException("DB connection lost"));

        // when
        DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);

        // then
        Diary result = processedDiary(outcome);
        assertThat(result.getId()).isNull();
        assertThat(result.getStatus()).isEqualTo(DiaryStatus.FAILED);
        verify(diaryRepository, times(1)).save(any(Diary.class));
    }

    @Test
    @DisplayName("getDiaries(days)는 오늘을 기준으로 today-(days-1)~today 범위로 위임한다")
    void getDiaries_delegatesToRangeQuery() {
        // given
        LocalDate expectedStart = TODAY.minusDays(29L);
        when(diaryRepository.findByUserIdAndDiaryDateBetweenOrderByDiaryDateDesc(TEST_USER_ID, expectedStart, TODAY))
                .thenReturn(List.of());

        // when
        diaryService.getDiaries(TEST_USER_ID, 30);

        // then
        verify(diaryRepository).findByUserIdAndDiaryDateBetweenOrderByDiaryDateDesc(TEST_USER_ID, expectedStart, TODAY);
    }

    @Test
    @DisplayName("getDiariesInRange는 지정한 시작/종료일 그대로 리포지토리에 위임한다")
    void getDiariesInRange_delegatesGivenRange() {
        // given
        LocalDate start = TODAY.minusMonths(1);
        LocalDate end = TODAY.minusDays(1);
        Diary diary = Diary.builder().userId(TEST_USER_ID).diaryDate(start).status(DiaryStatus.SUCCESS).build();
        when(diaryRepository.findByUserIdAndDiaryDateBetweenOrderByDiaryDateDesc(TEST_USER_ID, start, end))
                .thenReturn(List.of(diary));

        // when
        List<Diary> result = diaryService.getDiariesInRange(TEST_USER_ID, start, end);

        // then
        assertThat(result).containsExactly(diary);
    }

    @Test
    @DisplayName("getDiariesInRange에 endDate가 startDate보다 이전이면 InvalidDateRangeException을 던진다")
    void getDiariesInRange_throwsWhenEndBeforeStart() {
        // given
        LocalDate start = TODAY;
        LocalDate end = TODAY.minusDays(1);

        // when / then
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> diaryService.getDiariesInRange(TEST_USER_ID, start, end))
                .isInstanceOf(InvalidDateRangeException.class);
        verifyNoInteractions(diaryRepository);
    }

    private static Diary processedDiary(DiaryOutcome outcome) {
        assertThat(outcome).isInstanceOf(DiaryOutcome.Processed.class);
        return ((DiaryOutcome.Processed) outcome).diary();
    }
}
