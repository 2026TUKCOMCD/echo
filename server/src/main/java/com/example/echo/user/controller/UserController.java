package com.example.echo.user.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.user.dto.*;
import com.example.echo.user.service.UserService;
import jakarta.validation.Valid;
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
            @Valid @RequestBody UserPreferences request) {
        return ResponseEntity.ok(userService.savePreferences(userId, request));
    }

    @PutMapping("/preferences/birthday")
    public ResponseEntity<UserPreferences> updateBirthday(
            @CurrentUser Long userId,
            @Valid @RequestBody BirthdayUpdateRequest request) {
        return ResponseEntity.ok(userService.updateBirthday(userId, request.getBirthday()));
    }

    @PutMapping("/preferences/location")
    public ResponseEntity<UserPreferences> updateLocation(
            @CurrentUser Long userId,
            @Valid @RequestBody LocationUpdateRequest request) {
        return ResponseEntity.ok(userService.updateLocation(userId, request.getLocation()));
    }

    @PutMapping("/preferences/family-info")
    public ResponseEntity<UserPreferences> updateFamilyInfo(
            @CurrentUser Long userId,
            @Valid @RequestBody FamilyInfoUpdateRequest request) {
        return ResponseEntity.ok(userService.updateFamilyInfo(userId, request.getFamilyInfo()));
    }

    @PutMapping("/preferences/guardian-email")
    public ResponseEntity<UserPreferences> updateGuardianEmail(
            @CurrentUser Long userId,
            @Valid @RequestBody GuardianEmailUpdateRequest request) {
        return ResponseEntity.ok(userService.updateGuardianEmail(userId, request.getGuardianEmail()));
    }

    @PutMapping("/preferences/occupation")
    public ResponseEntity<UserPreferences> updateOccupation(
            @CurrentUser Long userId,
            @Valid @RequestBody OccupationUpdateRequest request) {
        return ResponseEntity.ok(userService.updateOccupation(userId, request.getOccupation()));
    }

    @PutMapping("/preferences/hobbies")
    public ResponseEntity<UserPreferences> updateHobbies(
            @CurrentUser Long userId,
            @Valid @RequestBody HobbiesUpdateRequest request) {
        return ResponseEntity.ok(userService.updateHobbies(userId, request.getHobbies()));
    }

    @PutMapping("/preferences/preferred-topics")
    public ResponseEntity<UserPreferences> updatePreferredTopics(
            @CurrentUser Long userId,
            @Valid @RequestBody PreferredTopicsUpdateRequest request) {
        return ResponseEntity.ok(userService.updatePreferredTopics(userId, request.getPreferredTopics()));
    }

    @PutMapping("/preferences/voice-settings")
    public ResponseEntity<UserPreferences> updateVoiceSettings(
            @CurrentUser Long userId,
            @Valid @RequestBody VoiceSettingsUpdateRequest request) {
        return ResponseEntity.ok(userService.updateVoiceSettings(userId, request.getVoiceSpeed(), request.getVoiceTone()));
    }

    @PutMapping("/preferences/conversation-time")
    public ResponseEntity<UserPreferences> updateConversationTime(
            @CurrentUser Long userId,
            @Valid @RequestBody ConversationTimeUpdateRequest request) {
        return ResponseEntity.ok(userService.updateConversationTime(userId, request.getConversationTime()));
    }

    @PutMapping("/preferences/preferred-sleep-hours")
    public ResponseEntity<UserPreferences> updatePreferredSleepHours(
            @CurrentUser Long userId,
            @Valid @RequestBody PreferredSleepHoursUpdateRequest request) {
        return ResponseEntity.ok(userService.updatePreferredSleepHours(userId, request.getPreferredSleepHours()));
    }

    @GetMapping("/onboarding-status")
    public ResponseEntity<OnboardingStatusResponse> getOnboardingStatus(@CurrentUser Long userId) {
        return ResponseEntity.ok(new OnboardingStatusResponse(userService.isOnboardingCompleted(userId)));
    }
}
