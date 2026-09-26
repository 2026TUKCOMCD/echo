/*
 * OpenWeatherMap API 클라이언트 설정
 * - 인증은 쿼리 파라미터(appid)로 전달되므로 별도 인터셉터가 필요 없다
 * - Request.Options: connect/read 타임아웃 명시 (기존엔 미설정 - Feign 기본값에 암묵적으로 의존했음)
 *
 * @Configuration 없음 - @FeignClient의 configuration= 속성으로만 사용
 * (컴포넌트 스캔 대상에서 제외하여 전역 Bean 등록 방지)
 */
package com.example.echo.common.client;

import org.springframework.context.annotation.Bean;

public class WeatherFeignConfig {

    /** connect 10s, read 30s - 다른 Feign 클라이언트(TTS/STT/Kakao)와 동일한 값. 단순 조회형 API라 여유 확보. */
    @Bean
    public feign.Request.Options weatherRequestOptions() {
        return new feign.Request.Options(10_000, 30_000);
    }
}
