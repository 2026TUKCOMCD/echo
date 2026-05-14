package com.example.echo.user.service;

import com.example.echo.auth.exception.UnauthorizedException;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.user.entity.User;
import com.example.echo.user.repository.UserPreferencesRepository;
import com.example.echo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserPreferencesRepository userPreferencesRepository;

    @Transactional(readOnly = true)
    public UserPreferences getPreferences(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));

        return userPreferencesRepository.findByUserId(userId)
                .map(prefs -> UserPreferences.builder()
                        .userId(user.getId())
                        .name(user.getName())
                        .age(prefs.getBirthday() != null
                                ? Period.between(prefs.getBirthday(), LocalDate.now()).getYears()
                                : null)
                        .birthday(prefs.getBirthday())
                        .location(prefs.getLocation())
                        .familyInfo(prefs.getFamilyInfo())
                        .guardianEmail(prefs.getGuardianEmail())
                        .occupation(prefs.getOccupation())
                        .hobbies(prefs.getHobbies())
                        .preferredTopics(prefs.getPreferredTopics())
                        .voiceSettings((prefs.getVoiceSpeed() != null || prefs.getVoiceTone() != null)
                                ? VoiceSettings.builder()
                                        .voiceSpeed(prefs.getVoiceSpeed())
                                        .voiceTone(prefs.getVoiceTone())
                                        .build()
                                : null)
                        .conversationTime(prefs.getConversationTime())
                        .preferredSleepHours(prefs.getPreferredSleepHours())
                        .build())
                .orElse(UserPreferences.builder()
                        .userId(user.getId())
                        .name(user.getName())
                        .build());
    }

    @Transactional
    public UserPreferences savePreferences(Long userId, UserPreferences request) {
        userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));

        Double voiceSpeed = request.getVoiceSettings() != null ? request.getVoiceSettings().getVoiceSpeed() : null;
        String voiceTone = request.getVoiceSettings() != null ? request.getVoiceSettings().getVoiceTone() : null;

        com.example.echo.user.entity.UserPreferences entity = userPreferencesRepository.findByUserId(userId)
                .orElse(null);

        if (entity == null) {
            entity = com.example.echo.user.entity.UserPreferences.builder()
                    .userId(userId)
                    .birthday(request.getBirthday())
                    .location(request.getLocation())
                    .familyInfo(request.getFamilyInfo())
                    .guardianEmail(request.getGuardianEmail())
                    .occupation(request.getOccupation())
                    .hobbies(request.getHobbies())
                    .preferredTopics(request.getPreferredTopics())
                    .voiceSpeed(voiceSpeed)
                    .voiceTone(voiceTone)
                    .conversationTime(request.getConversationTime())
                    .preferredSleepHours(request.getPreferredSleepHours())
                    .build();
        } else {
            entity.update(
                    request.getBirthday(),
                    request.getLocation(),
                    request.getFamilyInfo(),
                    request.getGuardianEmail(),
                    request.getOccupation(),
                    request.getHobbies(),
                    request.getPreferredTopics(),
                    voiceSpeed,
                    voiceTone,
                    request.getConversationTime(),
                    request.getPreferredSleepHours()
            );
        }

        userPreferencesRepository.save(entity);
        return getPreferences(userId);
    }

    @Transactional(readOnly = true)
    public boolean isOnboardingCompleted(Long userId) {
        return userPreferencesRepository.findByUserId(userId)
                .map(prefs -> prefs.getBirthday() != null
                        && prefs.getLocation() != null
                        && prefs.getConversationTime() != null)
                .orElse(false);
    }
}
