package com.example.echo.voice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Supertone Credit Balance API 응답 DTO.
 * GET /v1/credits — 로그 출력 전용, 앱에 노출되지 않음.
 * 공식 문서: https://docs.supertoneapi.com
 */
@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class SupertoneCreditBalance {

    @JsonProperty("balance")
    private Double balance;

    @Override
    public String toString() {
        return "SupertoneCreditBalance{balance=" + balance + '}';
    }
}
