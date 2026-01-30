package com.example.echo.user.service;

import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.dto.VoiceSettings;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;

@Service
public class UserService {

    public UserPreferences getPreferences(Long userId) {
        // TODO: 실제 DB 연동 시 Repository 사용
        return UserPreferences.builder()
                .userId(userId)
                .name("김영호")
                .age(68)
                .birthday(LocalDate.of(1957, 3, 15))
                .location("서울시 서초구")
                .familyInfo("아내, 아들 1명, 손녀 2명")
                .occupation("은퇴 (전 공무원)")
                .hobbies("등산, 바둑, 뉴스 보기")
                .preferredTopics("건강, 가족, 시사")
                .voiceSettings(VoiceSettings.builder()
                        .voiceSpeed(0.9)
                        .voiceTone("warm")
                        .build())
                .conversationTime(LocalTime.of(9, 0))
                .build();
    }
}