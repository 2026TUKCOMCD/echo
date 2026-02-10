package com.example.echo.voice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class ClovaFeignConfigTest {

    private ClovaFeignConfig config;

    @BeforeEach
    void setUp() {
        config = new ClovaFeignConfig();
        ReflectionTestUtils.setField(config, "clientId", "test-client-id");
        ReflectionTestUtils.setField(config, "clientSecret", "test-client-secret");
    }

    @Test
    @DisplayName("RequestInterceptor가 X-NCP-APIGW-API-KEY-ID 헤더를 추가한다")
    void addsClientIdHeader() {
        RequestInterceptor interceptor = config.clovaRequestInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-NCP-APIGW-API-KEY-ID");
        assertThat(values).containsExactly("test-client-id");
    }

    @Test
    @DisplayName("RequestInterceptor가 X-NCP-APIGW-API-KEY 헤더를 추가한다")
    void addsClientSecretHeader() {
        RequestInterceptor interceptor = config.clovaRequestInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-NCP-APIGW-API-KEY");
        assertThat(values).containsExactly("test-client-secret");
    }
}
