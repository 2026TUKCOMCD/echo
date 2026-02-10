package com.example.echo.prompt.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 프롬프트 템플릿 엔티티
 *
 * DB의 prompt_templates 테이블과 매핑
 * OpenAI API 호출 시 사용할 프롬프트 템플릿을 저장
 *
 * 템플릿 내용에는 {{변수명}} 형식의 플레이스홀더를 포함할 수 있으며,
 * compile() 메서드를 통해 실제 값으로 치환됨
 *
 * 예시:
 * - 템플릿: "{{userName}}님 안녕하세요"
 * - compile(Map.of("userName", "홍길동")) → "홍길동님 안녕하세요"
 */
@Entity
@Table(name = "prompt_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // JPA 기본 생성자 (protected로 외부 직접 생성 방지)
public class PromptTemplate {

    /**
     * 기본 키 (자동 증가)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "template_id")
    private Long id;

    /**
     * 프롬프트 타입 (SYSTEM, CONVERSATION, DIARY)
     * DB에는 문자열로 저장됨 (EnumType.STRING)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "template_type", nullable = false)
    private PromptType type;

    /**
     * 템플릿 내용
     * {{변수명}} 형식의 플레이스홀더 포함 가능
     */
    @Column(name = "template_content", columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * 템플릿 버전 (기본값: 1)
     * 동일 타입의 템플릿을 버전별로 관리할 때 사용
     */
    @Column(name = "version")
    private Integer version = 1;

    /**
     * 활성화 여부 (기본값: true)
     * false인 템플릿은 조회되지 않음
     */
    @Column(name = "is_active")
    private Boolean isActive = true;

    /**
     * 생성 일시 (자동 설정, 수정 불가)
     */
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /**
     * 엔티티 저장 전 자동으로 생성 시간 설정
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 빌더 패턴을 통한 객체 생성
     *
     * @param type     프롬프트 타입
     * @param content  템플릿 내용
     * @param version  버전 (null이면 1)
     * @param isActive 활성화 여부 (null이면 true)
     */
    @Builder
    public PromptTemplate(PromptType type, String content, Integer version, Boolean isActive) {
        this.type = type;
        this.content = content;
        this.version = version != null ? version : 1;
        this.isActive = isActive != null ? isActive : true;
    }

    /**
     * 템플릿 컴파일 - {{변수}}를 실제 값으로 치환
     *
     * 사용 예시:
     * Map<String, Object> variables = new HashMap<>();
     * variables.put("userName", "홍길동");
     * variables.put("userAge", 75);
     * String result = template.compile(variables);
     *
     * @param variables 변수명과 값의 매핑 (예: "userName" -> "홍길동")
     * @return 변수가 치환된 최종 프롬프트 문자열
     */
    public String compile(Map<String, Object> variables) {
        String result = this.content;

        // 모든 변수를 순회하며 {{변수명}}을 실제 값으로 치환
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";  // 예: {{userName}}
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }

        return result;
    }
}
