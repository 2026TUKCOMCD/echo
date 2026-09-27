/*
 * 장기기억 검색 설정값 (application.yaml: memory.recall.*)
 *
 * 임계값은 임베딩 모델·차원마다 점수 분포가 달라 openai.embedding을 바꾸면 다시 재야 한다.
 */
package com.example.echo.memory.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "memory.recall")
public class MemoryRecallProperties {

    /** 이 유사도 이상인 기억만 붙인다 */
    private double threshold;

    /** 한 번 검색에 붙이는 최대 건수 */
    private int topK;

    /** 이 수만큼의 첫 사용자 발화는 검색하지 않는다 */
    private int skipUserTurns;

    /** 검색하지 않는 맞장구 (공백·문장부호를 뺀 발화와 비교) */
    private Set<String> backchannels = Set.of();
}
