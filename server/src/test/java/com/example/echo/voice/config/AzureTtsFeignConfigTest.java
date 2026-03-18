package com.example.echo.voice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

class AzureTtsFeignConfigTest {

    private AzureTtsFeignConfig config;

    @BeforeEach
    void setUp() {
        config = new AzureTtsFeignConfig();
        ReflectionTestUtils.setField(config, "apiKey", "test-azure-api-key");
    }

    @Test
    @DisplayName("RequestInterceptor가 Ocp-Apim-Subscription-Key 헤더를 추가한다")
    void addsSubscriptionKeyHeader() {
        RequestInterceptor interceptor = config.azureTtsRequestInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Collection<String> values = template.headers().get("Ocp-Apim-Subscription-Key");
        assertThat(values).containsExactly("test-azure-api-key");
    }

    @Test
    @DisplayName("RequestInterceptor가 X-Microsoft-OutputFormat 헤더를 추가한다")
    void addsOutputFormatHeader() {
        RequestInterceptor interceptor = config.azureTtsRequestInterceptor();
        RequestTemplate template = new RequestTemplate();

        interceptor.apply(template);

        Collection<String> values = template.headers().get("X-Microsoft-OutputFormat");
        assertThat(values).containsExactly("audio-16khz-128kbitrate-mono-mp3");
    }
}
