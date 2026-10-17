package com.example.echo.common.http;

import org.apache.hc.core5.http.io.EofSensorInputStream;

import java.io.IOException;
import java.io.InputStream;

/**
 * HTTP 응답 스트림 도우미.
 *
 * feign-hc5(커넥션 풀링 ApacheHttp5Client)의 응답 close()는 연결을 재사용하려고 남은 본문을 끝까지 읽는다(drain).
 * LLM·TTS 스트림을 중간에 닫을 때 그대로 두면 상대가 생성을 마칠 때까지 닫기가 돌아오지 않고,
 * 상대는 끊긴 줄 모르고 끝까지 생성(과금)한다.
 */
public final class HttpStreams {

    private HttpStreams() {
    }

    /**
     * 다 읽지 않은 응답 본문이면 남은 본문을 읽지 않고 연결을 끊는다. 응답 close() 전에 부른다.
     *
     * 이미 끝까지 읽은 본문이면(연결은 그때 풀에 반납됨) 아무 일도 하지 않는다.
     * HC5 응답 본문이 아니면(테스트용 스트림, 다른 HTTP 클라이언트) 아무 일도 하지 않아 기존 동작대로 닫힌다.
     */
    public static void abortQuietly(InputStream body) {
        if (body instanceof EofSensorInputStream eofSensor) {
            try {
                eofSensor.abort();
            } catch (IOException ignored) {
                // 정리 중이므로 무시
            }
        }
    }
}
