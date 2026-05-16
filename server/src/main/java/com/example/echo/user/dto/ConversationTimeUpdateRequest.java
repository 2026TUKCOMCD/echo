package com.example.echo.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTimeUpdateRequest {
    @NotNull(message = "대화 시간은 필수입니다.")
    @JsonFormat(pattern = "HH:mm")
    private LocalTime conversationTime;
}
