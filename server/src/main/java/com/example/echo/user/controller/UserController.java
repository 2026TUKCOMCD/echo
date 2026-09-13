package com.example.echo.user.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.conversation.service.ConversationDataResetService;
import com.example.echo.conversation.service.ConversationDemoSeedService;
import com.example.echo.location.dto.GeocodingResult;
import com.example.echo.location.service.GeocodingService;
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
    private final GeocodingService geocodingService;
    private final ConversationDataResetService conversationDataResetService;
    private final ConversationDemoSeedService conversationDemoSeedService;

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

    @PutMapping("/preferences/home-location")
    public ResponseEntity<UserPreferences> updateHomeLocation(
            @CurrentUser Long userId,
            @Valid @RequestBody HomeLocationUpdateRequest request) {
        return ResponseEntity.ok(
                userService.updateHomeLocation(userId, request.getLatitude(), request.getLongitude()));
    }

    /**
     * 집 등록 직후 확인용 - 측정된 좌표를 역지오코딩해 사람이 읽을 수 있는 주소로 보여준다.
     * 저장은 하지 않는다(조회 전용). 실내 GPS 오차로 엉뚱한 곳이 등록됐을 때
     * 사용자가 바로 알아차릴 수 있게 하기 위함.
     */
    @GetMapping("/preferences/home-location/address")
    public ResponseEntity<GeocodingResult> previewHomeAddress(
            @RequestParam Double latitude,
            @RequestParam Double longitude) {
        return ResponseEntity.ok(geocodingService.reverseGeocode(latitude, longitude));
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

    @DeleteMapping("/conversation-data")
    public ResponseEntity<Void> resetConversationData(@CurrentUser Long userId) {
        conversationDataResetService.reset(userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 개발/데모 편의용: 경도인지장애 데모 페르소나(사용자 정보·루틴 방문 장소·오늘 건강 데이터)를
     * 한 번에 시딩한다. resetConversationData와 반대 방향이며 동일하게 본인 계정에 한해 동작한다.
     */
    @PostMapping("/demo-seed")
    public ResponseEntity<Void> seedDemoConversationData(@CurrentUser Long userId) {
        conversationDemoSeedService.seed(userId);
        return ResponseEntity.noContent().build();
    }
}
