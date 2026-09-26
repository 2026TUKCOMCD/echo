package com.example.echo.location.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Kakao API 인증 설정
 * - Authorization: KakaoAK {key} 헤더 자동 추가
 * - Request.Options: connect/read 타임아웃 명시 (기존엔 미설정 - Feign 기본값에 암묵적으로 의존했음)
 *
 * @Configuration 없음 - @FeignClient의 configuration= 속성으로만 사용
 * (컴포넌트 스캔 대상에서 제외하여 전역 Bean 등록 방지)
 */
public class KakaoFeignConfig {

    @Value("${kakao.api.key}")
    private String apiKey;

    @Bean
    public RequestInterceptor kakaoAuthInterceptor() {
        return template -> template.header("Authorization", "KakaoAK " + apiKey);
    }

    /** connect 10s, read 30s - 다른 Feign 클라이언트(TTS/STT)와 동일한 값. 단순 조회형 API라 여유 확보. */
    @Bean
    public feign.Request.Options kakaoRequestOptions() {
        return new feign.Request.Options(10_000, 30_000);
    }
}
