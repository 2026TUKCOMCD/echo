package com.example.echo.conversation.service;

import com.example.echo.context.service.ContextService;
import com.example.echo.diary.repository.DiaryRepository;
import com.example.echo.memory.service.MemoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ConversationDataResetServiceTest {

    private static final Long TEST_USER_ID = 1L;

    @Mock
    private MemoryService memoryService;

    @Mock
    private DiaryRepository diaryRepository;

    @Mock
    private ContextService contextService;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T01:00:00Z"), ZoneId.of("Asia/Seoul"));

    private ConversationDataResetService resetService;

    @BeforeEach
    void setUp() {
        resetService = new ConversationDataResetService(memoryService, diaryRepository, contextService, clock);
    }

    @Test
    @DisplayName("초기화하면 장기기억과 일기를 지우고 진행 중 세션도 정리한다")
    void reset_deletesMemoriesAndDiariesAndDiscardsSession() {
        // when
        resetService.reset(TEST_USER_ID);

        // then
        verify(memoryService).deleteAllMemories(TEST_USER_ID, LocalDateTime.of(2026, 9, 12, 10, 0));
        verify(diaryRepository).deleteByUserId(TEST_USER_ID);
        verify(contextService).finalizeContext(TEST_USER_ID);
    }
}
