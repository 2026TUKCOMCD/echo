package com.example.echo.prompt.entity;

/**
 * 프롬프트 템플릿 타입 열거형
 *
 * DB의 prompt_templates 테이블에서 template_type 컬럼에 저장되는 값
 * 각 타입별로 하나의 활성화된 템플릿만 사용됨
 */
public enum PromptType {

    /**
     * 시스템 프롬프트
     * - AI의 페르소나(역할, 성격) 정의
     * - 대화 규칙 및 금지 사항 명시
     * - 모든 대화의 기본이 되는 프롬프트
     */
    SYSTEM,

    /**
     * 대화 프롬프트
     * - 사용자 발화에 대한 AI 응답 생성용
     * - 시스템 프롬프트 + 오늘의 컨텍스트 + 대화 히스토리 + 사용자 메시지 조합
     */
    CONVERSATION,

    /**
     * 일기 생성 프롬프트
     * - 하루 대화 종료 후 일기 형식으로 요약
     * - 대화 히스토리를 기반으로 일기 생성
     */
    DIARY
}
