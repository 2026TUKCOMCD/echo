package com.example.echo.voice.realtime;

/**
 * 실시간 전사의 최종 결과
 *
 * @param text       최종 전사 (필터 적용 전)
 * @param avgLogprob 토큰 logprob 평균 - 모델이 logprobs를 주지 않으면 null. 신뢰도 기록용이며 필터에는 쓰지 않는다
 */
public record LiveTranscript(String text, Double avgLogprob) {
}
