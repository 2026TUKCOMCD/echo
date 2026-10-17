package com.example.echo.common.client;

import feign.FeignException;

/**
 * 외부 API 호출 실패를 로그에 남길 때 쓰는 요약.
 *
 * FeignException 메시지에는 요청 URL(쿼리 파라미터로 보내는 API 키·좌표)과 응답 본문이 그대로 들어 있어
 * 로그에 남기면 안 된다. 장애 파악에 필요한 예외 종류와 HTTP 상태만 남긴다.
 */
public final class ExternalApiErrors {

    private ExternalApiErrors() {
    }

    /** 예: "InternalServerError (HTTP 500)", "RetryableException - SocketTimeoutException" */
    public static String describe(Exception e) {
        if (!(e instanceof FeignException feign)) {
            return e.getClass().getSimpleName();
        }
        StringBuilder summary = new StringBuilder(feign.getClass().getSimpleName());
        if (feign.status() > 0) {
            summary.append(" (HTTP ").append(feign.status()).append(')');
        }
        if (feign.getCause() != null) {
            summary.append(" - ").append(feign.getCause().getClass().getSimpleName());
        }
        return summary.toString();
    }
}
