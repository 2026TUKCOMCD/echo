package com.example.echo.prompt.repository;

import com.example.echo.prompt.entity.PromptTemplate;
import com.example.echo.prompt.entity.PromptType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 프롬프트 템플릿 Repository
 *
 * JpaRepository를 상속받아 기본 CRUD 메서드 자동 제공
 * - save(), findById(), findAll(), delete() 등
 *
 * 추가로 프롬프트 조회용 커스텀 메서드 정의
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {

    /**
     * 타입과 활성화 상태로 템플릿 조회 (최신 1개)
     *
     * 실제 서비스에서 사용하는 메서드
     * 활성화된(is_active=true) 템플릿 중 가장 최근 것 조회
     *
     * 사용 예시:
     * Optional<PromptTemplate> template = repository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM);
     *
     * @param type 프롬프트 타입 (SYSTEM, CONVERSATION, DIARY)
     * @return 해당 타입의 활성화된 최신 템플릿 (없으면 Optional.empty())
     */
    Optional<PromptTemplate> findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType type);

    /**
     * 타입으로 템플릿 조회 (활성화 여부 무관)
     *
     * 관리/테스트 용도
     *
     * @param type 프롬프트 타입
     * @return 해당 타입의 템플릿
     */
    Optional<PromptTemplate> findByType(PromptType type);
}
