package com.example.echo.diary.service;

import com.example.echo.context.domain.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryService {

    public void generateAndSaveDiary(UserContext context) {

        log.info("DiaryService.generateAndSaveDiary 호출됨 - userId: {}, 대화 턴 수: {}",
                context.getUserId(),
                context.getConversationHistory().size());
        // TODO: 대화 내용 기반 일기 생성 및 저장
        log.info("TODO: 일기 생성 로직 구현 필요 - userId: {}", context.getUserId());

    }
}