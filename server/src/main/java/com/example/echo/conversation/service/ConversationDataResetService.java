package com.example.echo.conversation.service;

import com.example.echo.context.service.ContextService;
import com.example.echo.diary.repository.DiaryRepository;
import com.example.echo.memory.service.MemoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 체험 데이터 초기화 - 로그인·온보딩 정보는 두고 대화로 쌓인 장기기억·일기·진행 중 세션만 지운다.
 * UserService에 두면 ContextService → UserService 의존과 순환이 생겨 별도 서비스로 분리했다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationDataResetService {

    private final MemoryService memoryService;
    private final DiaryRepository diaryRepository;
    private final ContextService contextService;
    private final Clock clock;

    @Transactional
    public void reset(Long userId) {
        memoryService.deleteAllMemories(userId, LocalDateTime.now(clock));
        diaryRepository.deleteByUserId(userId);
        contextService.finalizeContext(userId);
        log.info("체험 데이터 초기화 완료 - userId: {}", userId);
    }
}
