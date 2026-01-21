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

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

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