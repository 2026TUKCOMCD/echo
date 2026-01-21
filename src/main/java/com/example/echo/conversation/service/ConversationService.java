package com.example.echo.conversation.service;

import com.example.echo.conversation.dto.ConversationResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 대화 처리 서비스 인터페이스
 * - 구현체는 개발자 A/C가 담당
 */
public interface ConversationService {

    /**
     * 사용자 음성 메시지 처리
     * - STT 변환 -> 프롬프트 생성 -> AI 응답 -> TTS 변환
     */
    ConversationResponse processUserMessage(Long userId, MultipartFile audioFile);
}
