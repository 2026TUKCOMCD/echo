/*
음성 설정 정보
*/
package com.example.echo.voice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class VoiceSettings {
    private Double speed;  //음성 속도
    private String voiceName; // 음성 종류
}
