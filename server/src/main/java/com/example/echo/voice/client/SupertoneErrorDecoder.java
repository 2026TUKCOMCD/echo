package com.example.echo.voice.client;

import com.example.echo.voice.exception.RetryableVoiceException;
import com.example.echo.voice.exception.SupertoneInsufficientCreditException;
import com.example.echo.voice.exception.VoiceProcessingException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SupertoneErrorDecoder implements ErrorDecoder {

    @Override
    public Exception decode(String methodKey, Response response) {
        int status = response.status();
        log.warn("Supertone API 오류 응답: method={}, status={}", methodKey, status);

        return switch (status) {
            case 402 -> new SupertoneInsufficientCreditException(
                    "Supertone 크레딧이 부족합니다. 관리자에게 문의해주세요.");
            case 429 -> new VoiceProcessingException(
                    "Supertone API 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요.");
            case 401 -> new VoiceProcessingException(
                    "Supertone API 인증에 실패했습니다.");
            case 400 -> new VoiceProcessingException(
                    "Supertone API 요청 형식이 올바르지 않습니다.");
            default -> {
                if (status >= 500) {
                    yield new RetryableVoiceException(
                            "Supertone 서버 오류가 발생했습니다. (HTTP " + status + ")");
                }
                yield new VoiceProcessingException(
                        "Supertone API 오류가 발생했습니다. (HTTP " + status + ")");
            }
        };
    }
}
