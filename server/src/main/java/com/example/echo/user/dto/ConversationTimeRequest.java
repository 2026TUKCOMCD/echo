package com.example.echo.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ConversationTimeRequest {

    @JsonFormat(pattern = "HH:mm")
    private LocalTime conversationTime;
}
