package com.example.echo.voice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAIFeignConfigTest {

    private OpenAIFeignConfig config;

    @BeforeEach
    void setUp() {
        config = new OpenAIFeignConfig();
        ReflectionTestUtils.setField(config, "apiKey", "test-api-key");
    }

    @Test
    @DisplayName("RequestInterceptor가 Authorization Bearer 헤더를 추가한다")
    void addsAuthorizationHeader() {
        RequestInterceptor interceptor = config.openAIRequestInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Collection<String> values = template.headers().get("Authorization");
        assertThat(values).containsExactly("Bearer test-api-key");
    }
}
