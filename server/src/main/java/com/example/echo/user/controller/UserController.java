package com.example.echo.user.controller;

import com.example.echo.user.dto.ConversationTimeRequest;
import com.example.echo.user.dto.ConversationTimeResponse;
import com.example.echo.user.service.UserPreferencesService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserPreferencesService userPreferencesService;

    @GetMapping("/conversation-time")
    public ResponseEntity<ConversationTimeResponse> getConversationTime(
            @RequestAttribute("userId") Long userId) {
        ConversationTimeResponse response = userPreferencesService.getConversationTime(userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/conversation-time")
    public ResponseEntity<ConversationTimeResponse> updateConversationTime(
            @RequestAttribute("userId") Long userId,
            @RequestBody ConversationTimeRequest request) {
        ConversationTimeResponse response = userPreferencesService.updateConversationTime(userId, request);
        return ResponseEntity.ok(response);
    }
}
