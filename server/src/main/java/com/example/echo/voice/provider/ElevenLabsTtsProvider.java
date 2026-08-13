package com.example.echo.voice.provider;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.client.ElevenLabsTtsClient;
import com.example.echo.voice.dto.ElevenLabsSubscriptionInfo;
import com.example.echo.voice.dto.ElevenLabsTtsRequest;
import com.example.echo.voice.exception.RetryableVoiceException;
import com.example.echo.voice.exception.VoiceProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ElevenLabs TTS 프로바이더.
 * Supertone 서비스 종료로 신규 도입, Supertone과 동일한 재시도/오류 처리 정책을 따른다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElevenLabsTtsProvider implements TtsProvider {

    private final ElevenLabsTtsClient elevenLabsClient;
    private final RetryTemplate elevenLabsRetryTemplate;

    @Value("${elevenlabs.voice-id}")
    private String voiceId;

    @Value("${elevenlabs.model:eleven_multilingual_v2}")
    private String model;

    private static final ElevenLabsTtsRequest.VoiceParams DEFAULT_VOICE_PARAMS =
        ElevenLabsTtsRequest.VoiceParams.builder().stability(0.7).similarityBoost(0.75).build();

    // voiceTone → ElevenLabs voice_settings(stability, similarity_boost) 매핑
    private static final Map<String, ElevenLabsTtsRequest.VoiceParams> TONE_TO_VOICE_PARAMS = Map.of(
        "warm",   ElevenLabsTtsRequest.VoiceParams.builder().stability(0.5).similarityBoost(0.75).build(),
        "calm",   ElevenLabsTtsRequest.VoiceParams.builder().stability(0.7).similarityBoost(0.75).build(),
        "bright", ElevenLabsTtsRequest.VoiceParams.builder().stability(0.3).similarityBoost(0.7).build(),
        "gentle", ElevenLabsTtsRequest.VoiceParams.builder().stability(0.6).similarityBoost(0.8).build()
    );

    @Override
    public String getName() {
        return "elevenlabs";
    }

    @Override
    public byte[] synthesize(String text, VoiceSettings voiceSettings) {
        if (voiceSettings != null && voiceSettings.getVoiceSpeed() != null
                && !voiceSettings.getVoiceSpeed().equals(1.0)) {
            log.warn("ElevenLabs TTS는 voiceSpeed 설정을 지원하지 않아 요청값({})이 무시됩니다.",
                voiceSettings.getVoiceSpeed());
        }

        ElevenLabsTtsRequest.VoiceParams voiceParams = resolveVoiceParams(voiceSettings);
        ElevenLabsTtsRequest request = ElevenLabsTtsRequest.builder()
            .text(text)
            .modelId(model)
            .voiceSettings(voiceParams)
            .build();

        log.info("ElevenLabs TTS 변환 시작: voice_id={}, model={}, stability={}, text_length={}",
            voiceId, model, voiceParams.getStability(), text.length());

        try {
            byte[] audioData = elevenLabsRetryTemplate.execute(ctx -> {
                if (ctx.getRetryCount() > 0) {
                    log.warn("ElevenLabs TTS 재시도 중: {}/2회", ctx.getRetryCount());
                }
                return elevenLabsClient.synthesize(voiceId, request);
            });

            if (audioData == null || audioData.length == 0) {
                throw new VoiceProcessingException("ElevenLabs TTS API 응답이 비어있습니다.");
            }

            log.info("ElevenLabs TTS 변환 완료: {} chars -> {} bytes", text.length(), audioData.length);
            return audioData;

        } catch (RetryableVoiceException e) {
            log.error("ElevenLabs TTS 3회 재시도 후 최종 실패: {}", e.getMessage());
            logQuota();
            throw new VoiceProcessingException(
                    "ElevenLabs TTS 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요.", e);
        } catch (VoiceProcessingException e) {
            logQuota();
            throw e;
        }
    }

    private ElevenLabsTtsRequest.VoiceParams resolveVoiceParams(VoiceSettings voiceSettings) {
        if (voiceSettings == null || voiceSettings.getVoiceTone() == null) return DEFAULT_VOICE_PARAMS;
        return TONE_TO_VOICE_PARAMS.getOrDefault(voiceSettings.getVoiceTone().toLowerCase(), DEFAULT_VOICE_PARAMS);
    }

    /**
     * TTS 실패 시 잔여 쿼터를 로그로 남겨 디버깅을 돕는다 (Supertone logCreditBalance 대응).
     */
    private void logQuota() {
        try {
            ElevenLabsSubscriptionInfo info = elevenLabsClient.getSubscriptionInfo();
            log.warn("[TTS 실패 - 디버깅용] ElevenLabs 잔여 쿼터: {}", info);
        } catch (Exception ex) {
            log.warn("[TTS 실패 - 디버깅용] ElevenLabs 쿼터 조회 실패: {}", ex.getMessage());
        }
    }
}
