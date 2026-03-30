package com.example.echo.conversation.dto;

import com.example.echo.health.dto.HealthData;
import com.example.echo.location.dto.RawLocationData;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "대화 시작 요청 DTO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationStartRequest {

    @Schema(description = "건강 데이터 (Health Connect에서 수집, null이면 DB에서 조회)")
    private HealthData healthData;

    @Schema(description = "위치 데이터 (null이면 위치 기반 대화 주제 생성 불가)")
    private RawLocationData locationData;
}
