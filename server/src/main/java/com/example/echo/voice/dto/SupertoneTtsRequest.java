package com.example.echo.voice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupertoneTtsRequest {
    private String text;
    private String language;
    private String style;
    private String model;
    private Double speed;
}
