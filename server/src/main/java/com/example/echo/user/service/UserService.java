package com.example.echo.user.service;

import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
public class UserService {

    public UserPreferences getPreferences(Long userId) {
        return UserPreferences.builder()
                .userId(userId)
                .name("이형석")
                .age(68)
                .birthday(LocalDate.of(1957, 3, 15))
                .location("수원시 권선구")
                .familyInfo("어머니, 아버지, 누나")
                .occupation("대학생")
                .hobbies("독서, 산책")
                .preferredTopics("건강, 독서, 시사")
                .voiceSettings(VoiceSettings.builder()
                        .voiceSpeed(0.9)
                        .voiceTone("warm")
                        .build())
                .conversationTime(LocalTime.of(9, 0))
                .preferredSleepHours(7)  // 선호 수면 시간: 7시간
                .build();
    }
}