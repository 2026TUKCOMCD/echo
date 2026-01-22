package com.example.echo.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {
    private Long userId;
    private String name;
    private Integer age;
    private String location;
    private String familyInfo;
    private String occupation;
    private String hobbies;
    private String preferredTopics;
    private VoiceSettings voiceSettings;
    private LocalTime conversationTime;
}