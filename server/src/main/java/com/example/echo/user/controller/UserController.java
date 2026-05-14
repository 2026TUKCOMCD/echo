package com.example.echo.user.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.user.dto.ConversationTimeRequest;
import com.example.echo.user.dto.ConversationTimeResponse;
import com.example.echo.user.dto.OnboardingStatusResponse;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/preferences")
    public ResponseEntity<UserPreferences> getPreferences(@CurrentUser Long userId) {
        return ResponseEntity.ok(userService.getPreferences(userId));
    }

    @PutMapping("/preferences")
    public ResponseEntity<UserPreferences> updatePreferences(
            @CurrentUser Long userId,
            @RequestBody UserPreferences request) {
        return ResponseEntity.ok(userService.savePreferences(userId, request));
    }

    @PutMapping("/conversation-time")
    public ResponseEntity<ConversationTimeResponse> updateConversationTime(
            @CurrentUser Long userId,
            @RequestBody ConversationTimeRequest request) {
        return ResponseEntity.ok(userService.updateConversationTime(userId, request));
    }

    @PutMapping("/voice-settings")
    public ResponseEntity<VoiceSettings> updateVoiceSettings(
            @CurrentUser Long userId,
            @RequestBody VoiceSettings request) {
        return ResponseEntity.ok(userService.updateVoiceSettings(userId, request));
    }

    @GetMapping("/onboarding-status")
    public ResponseEntity<OnboardingStatusResponse> getOnboardingStatus(@CurrentUser Long userId) {
        return ResponseEntity.ok(new OnboardingStatusResponse(userService.isOnboardingCompleted(userId)));
    }
}
