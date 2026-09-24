/*
 * 스트리밍 대화 응답 설정값 (application.yaml: conversation.stream.*)
 *
 * first-chunk-min-chars: 첫 TTS 조각의 최소 글자 수 - 첫 문장이 이보다 짧으면 다음 문장까지 붙인다
 * first-chunk-max-chars: 문장 끝 없이 이 글자 수를 넘으면 쉼표/공백에서 강제로 자른다
 * llm-timeout-seconds:   LLM 스트림의 첫 조각/완료를 기다리는 최대 시간
 */
package com.example.echo.conversation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "conversation.stream")
public class ConversationStreamProperties {

    private int firstChunkMinChars = 15;
    private int firstChunkMaxChars = 80;
    private long llmTimeoutSeconds = 90;
}
