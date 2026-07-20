package com.example.echo.diary.service;

import com.example.echo.ai.exception.AIException;
import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.diary.entity.Diary;
import com.example.echo.diary.entity.DiaryStatus;
import com.example.echo.diary.repository.DiaryRepository;
import com.example.echo.prompt.service.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
        when(aiService.generateDiary("일기 프롬프트")).thenReturn("오늘은 산책을 다녀왔다.");
        when(diaryRepository.save(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Diary result = diaryService.generateAndSaveDiary(context);

        // then
        assertThat(result).isNotNull();
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
        when(aiService.generateDiary("증분 프롬프트")).thenReturn("아침 산책과 오후 이야기를 담은 통합 일기");
        when(diaryRepository.save(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Diary result = diaryService.generateAndSaveDiary(context);

        // then
        verify(promptService).buildDiaryPrompt(eq(context), eq("아침에 쓴 기존 일기"));
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
        when(aiService.generateDiary(anyString())).thenThrow(new AIException("AI 일기 생성 실패: 401"));
        when(diaryRepository.save(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        Diary result = diaryService.generateAndSaveDiary(context);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(DiaryStatus.FAILED);
        assertThat(result.getFailureReason()).contains("AI 일기 생성 실패");
        assertThat(result.getContent()).isEqualTo("아침에 성공한 일기"); // 성공본 보존
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
        Diary result = diaryService.generateAndSaveDiary(context);

        // then
        assertThat(result).isNull();
        verifyNoInteractions(aiService);
        verify(diaryRepository, never()).save(any());
    }
}
