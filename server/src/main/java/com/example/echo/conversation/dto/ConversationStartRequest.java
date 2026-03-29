package com.example.echo.conversation.dto;

import com.example.echo.health.dto.HealthData;
import com.example.echo.location.dto.RawLocationData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 대화 시작 요청 DTO
 *
 * 앱에서 전송하는 오늘 건강 데이터를 수신
 * 대화 시작 시 건강 데이터를 즉시 저장하여 손실 방지
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationStartRequest {

    /**
     * 앱에서 전송하는 오늘 건강 데이터
     * - Health Connect에서 수집된 데이터
     * - null인 경우 DB에서 기존 데이터 조회
     */
    private HealthData healthData;

    /**
     * 앱에서 전송하는 위치 데이터 (null 허용)
     * - null인 경우 위치 기반 대화 주제 생성 불가
     */
    private RawLocationData locationData;
}
