package com.example.echo.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferences {
    private Long userId;
    private String name;
    private Integer age;
    private LocalDate birthday;
    private String location;
    private String familyInfo;
    private String guardianEmail;
    private String occupation;
    private String hobbies;
    private String preferredTopics;
    private VoiceSettings voiceSettings;
    @JsonFormat(pattern = "HH:mm")
    private LocalTime conversationTime;
    private Integer preferredSleepHours;
}