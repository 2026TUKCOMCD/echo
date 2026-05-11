package com.example.echo.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalTime;

@Getter
@AllArgsConstructor
public class ConversationTimeResponse {

    @JsonFormat(pattern = "HH:mm")
    private LocalTime conversationTime;

    public static ConversationTimeResponse of(LocalTime conversationTime) {
        return new ConversationTimeResponse(conversationTime);
    }
}
