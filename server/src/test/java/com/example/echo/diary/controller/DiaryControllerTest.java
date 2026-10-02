package com.example.echo.diary.controller;

import com.example.echo.common.auth.CurrentUserArgumentResolver;
import com.example.echo.common.exception.GlobalExceptionHandler;
import com.example.echo.diary.entity.Diary;
import com.example.echo.diary.entity.DiaryStatus;
import com.example.echo.diary.exception.DiaryNotFoundException;
import com.example.echo.diary.exception.InvalidDateRangeException;
import com.example.echo.diary.service.DiaryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("DiaryController - 일기 조회 API")
class DiaryControllerTest {

    private static final Long USER_ID = 1L;

    @Mock
    private DiaryService diaryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DiaryController(diaryService))
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Diary diary(LocalDate date, DiaryStatus status, String content, String failureReason) {
        return Diary.builder()
                .userId(USER_ID)
                .diaryDate(date)
                .title("제목")
                .content(content)
                .status(status)
                .failureReason(failureReason)
                .build();
    }

    @Test
    @DisplayName("startDate/endDate를 모두 주면 그 범위로 조회하고, 실패 기록도 status/failureReason과 함께 내려준다")
    void getDiaries_withRange() throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 1);
        LocalDate end = LocalDate.of(2026, 9, 30);
        when(diaryService.getDiariesInRange(USER_ID, start, end)).thenReturn(List.of(
                diary(LocalDate.of(2026, 9, 20), DiaryStatus.SUCCESS, "내용", null),
                diary(LocalDate.of(2026, 9, 3), DiaryStatus.FAILED, null, "LLM 오류")
        ));

        mockMvc.perform(get("/api/diaries").param("startDate", "2026-09-01").param("endDate", "2026-09-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-09-20"))
                .andExpect(jsonPath("$[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$[1].status").value("FAILED"))
                .andExpect(jsonPath("$[1].failureReason").value("LLM 오류"));

        verify(diaryService, never()).getDiaries(anyLong(), anyInt());
    }

    @Test
    @DisplayName("날짜 범위가 없으면 days(기본 30)로 조회한다")
    void getDiaries_defaultDays() throws Exception {
        when(diaryService.getDiaries(USER_ID, 30)).thenReturn(List.of());

        mockMvc.perform(get("/api/diaries"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("endDate가 startDate보다 앞이면 400")
    void getDiaries_invalidRange() throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 30);
        LocalDate end = LocalDate.of(2026, 9, 1);
        when(diaryService.getDiariesInRange(USER_ID, start, end)).thenThrow(new InvalidDateRangeException(start, end));

        mockMvc.perform(get("/api/diaries").param("startDate", "2026-09-30").param("endDate", "2026-09-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("단건 조회는 본인 일기가 아니거나 없으면 404")
    void getDiary_notFound() throws Exception {
        when(diaryService.getDiary(99L, USER_ID)).thenThrow(new DiaryNotFoundException(99L));

        mockMvc.perform(get("/api/diaries/99"))
                .andExpect(status().isNotFound());
    }
}
