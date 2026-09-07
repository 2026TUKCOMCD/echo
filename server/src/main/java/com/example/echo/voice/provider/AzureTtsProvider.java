package com.example.echo.voice.provider;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.client.TTSClient;
import com.example.echo.voice.exception.VoiceProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Azure Cognitive Services TTS 프로바이더.
 * SSML(XML) 형식의 텍스트를 음성(MP3)으로 변환한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AzureTtsProvider implements TtsProvider {

    private final TTSClient ttsClient;

    @Value("${azure.tts.default-voice:ko-KR-SunHiNeural}")
    private String defaultVoice;

    // voiceTone → Azure Neural Voice 매핑
    private static final Map<String, String> TONE_TO_VOICE = Map.of(
        "warm",   "ko-KR-SunHiNeural",   // 친근하고 따뜻한 여성
        "calm",   "ko-KR-InJoonNeural",  // 차분한 남성
        "bright", "ko-KR-JiMinNeural",   // 밝고 활기찬 여성
        "gentle", "ko-KR-YuJinNeural"    // 부드러운 여성
    );

    @Override
    public String getName() {
        return "azure";
    }

    @Override
    public byte[] synthesize(String text, VoiceSettings voiceSettings) {
        String voiceName = resolveVoice(voiceSettings);
        String rate = convertSpeedToRate(voiceSettings);
        String ssml = buildSsml(text, voiceName, rate);

        log.info("Azure TTS 변환 시작: voice={}, rate={}, text_length={}",
            voiceName, rate, text.length());

        byte[] audioData = ttsClient.synthesize(ssml);

        if (audioData == null || audioData.length == 0) {
            throw new VoiceProcessingException("Azure TTS API 응답이 비어있습니다.");
        }

        log.info("Azure TTS 변환 완료: {} chars -> {} bytes", text.length(), audioData.length);
        return audioData;
    }

    private String resolveVoice(VoiceSettings voiceSettings) {
        if (voiceSettings == null || voiceSettings.getVoiceTone() == null) return defaultVoice;
        return TONE_TO_VOICE.getOrDefault(voiceSettings.getVoiceTone().toLowerCase(), defaultVoice);
    }

    private String convertSpeedToRate(VoiceSettings voiceSettings) {
        if (voiceSettings == null || voiceSettings.getVoiceSpeed() == null) return "+0%";
        // voiceSpeed: 0.5 ~ 2.0 (기본 1.0) → SSML rate: -50% ~ +100% (기본 +0%)
        int ratePercent = (int) Math.round((voiceSettings.getVoiceSpeed() - 1.0) * 100);
        ratePercent = Math.max(-50, Math.min(100, ratePercent));
        return (ratePercent >= 0 ? "+" : "") + ratePercent + "%";
    }

    private String buildSsml(String text, String voiceName, String rate) {
        // XML 특수문자 이스케이프 (SSML 파싱 오류 방지)
        String escaped = text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
        return String.format(
            "<speak version='1.0' xml:lang='ko-KR'><voice xml:lang='ko-KR' name='%s'>" +
            "<prosody rate='%s'>%s</prosody></voice></speak>",
            voiceName, rate, escaped
        );
    }
}
