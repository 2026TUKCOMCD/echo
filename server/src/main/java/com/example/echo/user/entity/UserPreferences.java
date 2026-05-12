package com.example.echo.user.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "user_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPreferences {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "family_info", length = 500)
    private String familyInfo;

    @Column(name = "guardian_email", length = 255)
    private String guardianEmail;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "hobbies", length = 500)
    private String hobbies;

    @Column(name = "preferred_topics", length = 500)
    private String preferredTopics;

    @Column(name = "voice_speed")
    private Double voiceSpeed;

    @Column(name = "voice_tone", length = 50)
    private String voiceTone;

    @Column(name = "conversation_time")
    private LocalTime conversationTime;

    @Column(name = "preferred_sleep_hours")
    private Integer preferredSleepHours;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    @Builder
    public UserPreferences(Long userId, LocalDate birthday, String location,
                           String familyInfo, String guardianEmail, String occupation,
                           String hobbies, String preferredTopics, Double voiceSpeed,
                           String voiceTone, LocalTime conversationTime,
                           Integer preferredSleepHours) {
        this.userId = userId;
        this.birthday = birthday;
        this.location = location;
        this.familyInfo = familyInfo;
        this.guardianEmail = guardianEmail;
        this.occupation = occupation;
        this.hobbies = hobbies;
        this.preferredTopics = preferredTopics;
        this.voiceSpeed = voiceSpeed;
        this.voiceTone = voiceTone;
        this.conversationTime = conversationTime;
        this.preferredSleepHours = preferredSleepHours;
    }

    public void update(LocalDate birthday, String location, String familyInfo,
                       String guardianEmail, String occupation, String hobbies,
                       String preferredTopics, Double voiceSpeed, String voiceTone,
                       LocalTime conversationTime, Integer preferredSleepHours) {
        this.birthday = birthday;
        this.location = location;
        this.familyInfo = familyInfo;
        this.guardianEmail = guardianEmail;
        this.occupation = occupation;
        this.hobbies = hobbies;
        this.preferredTopics = preferredTopics;
        this.voiceSpeed = voiceSpeed;
        this.voiceTone = voiceTone;
        this.conversationTime = conversationTime;
        this.preferredSleepHours = preferredSleepHours;
        this.updatedAt = LocalDateTime.now();
    }
}
