package com.example.echo.conversation.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.conversation.dto.ConversationEndResponse;
import com.example.echo.conversation.dto.ConversationResponse;
import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 대화 처리 컨트롤러
 * - /message 엔드포인트 담당 (T3.2-4)
 * - /start, /end는 개발자 A 담당
 */
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 사용자 음성 메시지 처리
     * - MultipartFile로 음성 파일 수신
     * - STT -> AI 응답 -> TTS 처리 후 반환
     */
    @PostMapping("/start")
    public ResponseEntity<ConversationStartResponse> startConversation(
            @CurrentUser Long userId
    ) {
        ConversationStartResponse response = conversationService.startConversation(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/message")
    public ResponseEntity<ConversationResponse> processMessage(
            @CurrentUser Long userId,
            @RequestPart("audio") MultipartFile audioFile
    ) {
        ConversationResponse response = conversationService.processUserMessage(userId, audioFile);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/end")
    public ResponseEntity<ConversationEndResponse> endConversation(
            @CurrentUser Long userId
    ) {
        conversationService.endConversation(userId);
        ConversationEndResponse response = ConversationEndResponse.builder()
                .endedAt(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(response);
    }
}
