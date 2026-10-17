package com.example.echo.diary.service;

import com.example.echo.ai.service.AIService;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.diary.entity.Diary;
import com.example.echo.diary.entity.DiaryStatus;
import com.example.echo.diary.repository.DiaryRepository;
import com.example.echo.prompt.service.PromptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * DiaryService를 Spring 프록시(트랜잭션 적용)를 거쳐 호출해, LLM 호출이 트랜잭션 밖에서 일어나는지 확인한다.
 * 트랜잭션 안에서 LLM을 기다리면 생성 시간(수십 초) 동안 DB 커넥션을 점유한다.
 */
@SpringJUnitConfig(DiaryServiceTransactionTest.Config.class)
@DisplayName("DiaryService - 트랜잭션 범위")
class DiaryServiceTransactionTest {

    @Configuration
    @EnableTransactionManagement
    static class Config {
        @Bean
        DiaryRepository diaryRepository() {
            return mock(DiaryRepository.class);
        }

        @Bean
        PromptService promptService() {
            return mock(PromptService.class);
        }

        @Bean
        AIService aiService() {
            return mock(AIService.class);
        }

        @Bean
        DiaryService diaryService(DiaryRepository diaryRepository, PromptService promptService, AIService aiService) {
            return new DiaryService(diaryRepository, promptService, aiService);
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return new NoOpTransactionManager();
        }
    }

    /** 실제 DB 없이 트랜잭션 경계만 표시하는 트랜잭션 매니저 */
    static class NoOpTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }

    @Autowired
    private DiaryService diaryService;
    @Autowired
    private DiaryRepository diaryRepository;
    @Autowired
    private PromptService promptService;
    @Autowired
    private AIService aiService;

    @Test
    @DisplayName("일기를 LLM으로 생성하는 동안에는 트랜잭션(DB 커넥션)을 잡고 있지 않다")
    void llmCallRunsOutsideTransaction() {
        UserContext context = UserContext.builder()
                .userId(1L)
                .conversationHistory(new CopyOnWriteArrayList<>())
                .build();
        context.getConversationHistory().add(ConversationTurn.builder()
                .userMessage("오늘 산책 다녀왔어요")
                .aiResponse("좋으셨겠어요!")
                .timestamp(LocalDateTime.now())
                .build());
        AtomicBoolean transactionActiveDuringLlm = new AtomicBoolean(true);
        when(diaryRepository.findByUserIdAndDiaryDate(any(), any())).thenReturn(Optional.empty());
        when(promptService.buildDiaryPrompt(any(), any())).thenReturn("프롬프트");
        when(aiService.generateDiary(anyString())).thenAnswer(inv -> {
            transactionActiveDuringLlm.set(TransactionSynchronizationManager.isActualTransactionActive());
            return "오늘은 산책을 다녀왔다.";
        });
        when(diaryRepository.save(any(Diary.class))).thenAnswer(inv -> inv.getArgument(0));

        DiaryOutcome outcome = diaryService.generateAndSaveDiary(context);

        assertThat(transactionActiveDuringLlm).isFalse();
        assertThat(((DiaryOutcome.Processed) outcome).diary().getStatus()).isEqualTo(DiaryStatus.SUCCESS);
    }
}
