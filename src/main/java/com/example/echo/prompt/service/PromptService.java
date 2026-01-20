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
                .orElseThrow(() -> new IllegalStateException(
                        "활성화된 SYSTEM 프롬프트 템플릿이 없습니다."));

        // 2. 컨텍스트에서 변수 추출
        Map<String, Object> variables = new HashMap<>();
        variables.put("userName", context.getUserName());
        variables.put("userAge", context.getUserAge());

        // 3. 템플릿 컴파일 (변수 치환) 후 반환
        return template.compile(variables);
    }
}
