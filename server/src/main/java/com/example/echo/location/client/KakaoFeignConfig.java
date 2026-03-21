package com.example.echo.location.client;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Kakao API 인증 설정
 * - Authorization: KakaoAK {key} 헤더 자동 추가
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
}
