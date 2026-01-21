package com.example.echo.prompt.service;

import com.example.echo.prompt.dto.PromptContext;
import com.example.echo.prompt.entity.PromptTemplate;
import com.example.echo.prompt.entity.PromptType;
import com.example.echo.prompt.repository.PromptTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 프롬프트 서비스
 *
 * 역할: 대화용 프롬프트 생성
 * - DB에서 프롬프트 템플릿 조회
 * - 컨텍스트 데이터로 변수 치환
 * - 최종 프롬프트 문자열 반환
 *
 * 일기 프롬프트는 DiaryService에서 담당 (단일 책임 원칙)
 */
@Service
@RequiredArgsConstructor
public class PromptService {

    private final PromptTemplateRepository promptTemplateRepository;

    /**
     * 시스템 프롬프트 생성
     *
     * AI의 페르소나와 대화 규칙을 정의하는 프롬프트
     * 모든 대화의 기본이 되며, 대화 시작 시 1회 생성
     *
     * 템플릿 변수: {{userName}}, {{userAge}}
     *
     * @param context 프롬프트 생성에 필요한 컨텍스트
     * @return 컴파일된 시스템 프롬프트 문자열
     * @throws IllegalStateException 활성화된 SYSTEM 템플릿이 없을 경우
     */
    public String buildSystemPrompt(PromptContext context) {
        // 1. DB에서 활성화된 SYSTEM 템플릿 조회
        PromptTemplate template = promptTemplateRepository
                .findByTypeAndIsActiveTrue(PromptType.SYSTEM)
                .orElseThrow(() -> new IllegalStateException( //.orElseThrow를 사용할 수 있는 이유가 Repository에서 Optional로 감쌌기 때문
                        "활성화된 SYSTEM 프롬프트 템플릿이 없습니다."));

        // 2. 컨텍스트에서 변수 추출
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", context.getUserName());
        variables.put("userAge", context.getUserAge());

        // 3. 템플릿 컴파일 (변수 치환) 후 반환
        return template.compile(variables);
    }

    /**
     * 대화 프롬프트 생성
     *
     * 사용자 발화에 대한 AI 응답 생성을 위한 프롬프트
     * 시스템 프롬프트 + 오늘의 컨텍스트 + 대화 히스토리 + 사용자 메시지 조합
     *
     * 템플릿 변수: {{systemPrompt}}, {{todayContext}}, {{conversationHistory}}, {{userMessage}}
     *
     * @param context 프롬프트 생성에 필요한 컨텍스트
     * @param userMessage 현재 사용자 발화 (STT 변환 결과)
     * @return 컴파일된 대화 프롬프트 문자열
     * @throws IllegalStateException 활성화된 CONVERSATION 템플릿이 없을 경우
     */
    public String buildConversationPrompt(PromptContext context, String userMessage) {
        // 1. DB에서 활성화된 CONVERSATION 템플릿 조회
        PromptTemplate template = promptTemplateRepository
                .findByTypeAndIsActiveTrue(PromptType.CONVERSATION)
                .orElseThrow(() -> new IllegalStateException(
                        "활성화된 CONVERSATION 프롬프트 템플릿이 없습니다."));

        // 2. 각 구성 요소 빌드
        String systemPrompt = buildSystemPrompt(context);
        String todayContext = buildTodayContext(context);
        String conversationHistory = buildHistory(context);

        // 3. 템플릿 변수 매핑
        Map<String, Object> variables = new HashMap<>();
        variables.put("systemPrompt", systemPrompt);
        variables.put("todayContext", todayContext);
        variables.put("conversationHistory", conversationHistory);
        variables.put("userMessage", userMessage);

        // 4. 템플릿 컴파일 (변수 치환) 후 반환
        return template.compile(variables);
    }

    /**
     * 오늘의 컨텍스트 빌드 (건강 데이터 + 날씨)
     *
     * @param context 프롬프트 컨텍스트
     * @return 포맷팅된 오늘의 컨텍스트 문자열
     */
    private String buildTodayContext(PromptContext context) {
        // TODO: T3.3-5에서 구현 예정
        return "";
    }

    /**
     * 대화 히스토리 빌드
     *
     * @param context 프롬프트 컨텍스트
     * @return 포맷팅된 대화 히스토리 문자열
     */
    private String buildHistory(PromptContext context) {
        // TODO: T3.3-6에서 구현 예정
        return "";
    }
}
