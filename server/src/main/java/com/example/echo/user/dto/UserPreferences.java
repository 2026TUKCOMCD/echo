package com.example.echo.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @NotNull(message = "생년월일은 필수입니다.")
    private LocalDate birthday;

    @NotNull(message = "거주 지역은 필수입니다.")
    @Size(max = 100)
    private String location;

    @Size(max = 500)
    private String familyInfo;

    @Email(message = "보호자 이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    private String guardianEmail;

    @Size(max = 100)
    private String occupation;

    @Size(max = 500)
    private String hobbies;

    @Size(max = 500)
    private String preferredTopics;

    private VoiceSettings voiceSettings;

    @NotNull(message = "대화 시간은 필수입니다.")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime conversationTime;

    private Integer preferredSleepHours;
}