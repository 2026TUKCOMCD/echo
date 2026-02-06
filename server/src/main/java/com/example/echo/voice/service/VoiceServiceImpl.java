/*
STTclient, TTS client 실행하는 곳

 "음성 변환 작업반장"
   * - 파일이 올바른지 검사하고
   * - STT/TTS 클라이언트에게 작업 지시
   * - 결과를 정리해서 반환
*/
package com.example.echo.voice.service;

import com.example.echo.voice.client.STTClient;
import com.example.echo.voice.client.TTSClient;
// [2024-01 merge] voice.dto.VoiceSettings → user.dto.VoiceSettings로 통일
// 이유: user/dto에 더 완성도 높은 VoiceSettings가 있어 중복 제거
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.dto.WhisperTranscriptionResponse;
import com.example.echo.voice.exception.VoiceProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoiceService {

    private final STTClient sttClient;
    private final TTSClient ttsClient;

    @Value("${openai.whisper.model:whisper-1}")
    private String whisperModel;

    @Value("${openai.whisper.language:ko}")
    private String defaultLanguage;

    @Value("${clova.tts.speaker:nara}")
    private String defaultSpeaker;

    // voiceTone → Clova speaker 매핑
    private static final Map<String, String> TONE_TO_SPEAKER = Map.of(
        "warm", "nara",       // 따뜻한 여성 음성
        "calm", "vyuna",      // 차분한 여성 음성
        "bright", "vdain",    // 밝은 여성 음성
        "gentle", "nminyoung" // 부드러운 여성 음성
    );

    /*
     * ========== STT (음성 → 텍스트) ==========
     *
     * [메인 흐름]
     * 1. 입력: MultipartFile audioFile (음성 파일)
     * 2. 검증: validateAudioFile() - 파일 존재/형식/크기 확인
     * 3. 전처리: 없음 (파일 그대로 전송)
     * 4. API 호출: sttClient.transcribe() → OpenAI Whisper API
     * 5. 응답: WhisperTranscriptionResponse (JSON)
     * 6. 출력: String (변환된 텍스트)
     */
    @Override
    public String speechToText(MultipartFile audioFile) {
        // 1. 검증
        validateAudioFile(audioFile);

        try {
            // 2. API 호출
            WhisperTranscriptionResponse response = sttClient.transcribe(
                    audioFile,
                    whisperModel,
                    defaultLanguage,
                    "json"
            );

            // 3. 응답 확인
            if (response == null || response.getText() == null) {
                throw new VoiceProcessingException("Whisper API 응답이 비어있습니다.");
            }

            log.info("STT 변환 완료: {} bytes -> {} chars",
                    audioFile.getSize(),
                    response.getText().length());

            return response.getText();

        } catch (VoiceProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("STT 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new VoiceProcessingException("음성을 텍스트로 변환하는 중 오류가 발생했습니다.", e);
        }
    }

    /*
     * ========== TTS (텍스트 → 음성) ==========
     *
     * [메인 흐름]
     * 1. 입력: String text, VoiceSettings voiceSettings
     * 2. 검증: validateText() - 빈값/글자수 확인
     * 3. 전처리:
     *    - resolveSpeaker(): voiceTone → Clova speaker 변환
     *    - convertSpeed(): voiceSpeed → Clova speed 변환
     *    - buildFormData(): API 요청 형식으로 조합
     * 4. API 호출: ttsClient.synthesize() → Clova TTS API
     * 5. 응답: byte[] (MP3 바이너리)
     * 6. 출력: byte[] (음성 파일)
     */
    @Override
    public byte[] textToSpeech(String text, VoiceSettings voiceSettings) {
        // 1. 검증
        validateText(text);

        try {
            // 2. 전처리 - 사용자 설정을 Clova API 형식으로 변환
            String speaker = resolveSpeaker(voiceSettings);
            int speed = convertSpeed(voiceSettings);
            String formData = buildFormData(text, speaker, speed);

            log.info("TTS 변환 시작: speaker={}, speed={}, text_length={}",
                    speaker, speed, text.length());

            // 3. API 호출
            byte[] audioData = ttsClient.synthesize(formData);

            // 4. 응답 확인
            if (audioData == null || audioData.length == 0) {
                throw new VoiceProcessingException("Clova TTS API 응답이 비어있습니다.");
            }

            log.info("TTS 변환 완료: {} chars -> {} bytes",
                    text.length(), audioData.length);

            return audioData;

        } catch (VoiceProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("TTS 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new VoiceProcessingException("텍스트를 음성으로 변환하는 중 오류가 발생했습니다.", e);
        }
    }

    private void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new VoiceProcessingException("변환할 텍스트가 비어있습니다.");
        }
        // Clova TTS 최대 글자수: 약 2000자
        if (text.length() > 2000) {
            throw new VoiceProcessingException("텍스트가 2000자를 초과합니다.");
        }
    }

    private String resolveSpeaker(VoiceSettings voiceSettings) {
        if (voiceSettings == null || voiceSettings.getVoiceTone() == null) {
            return defaultSpeaker;
        }
        return TONE_TO_SPEAKER.getOrDefault(
            voiceSettings.getVoiceTone().toLowerCase(),
            defaultSpeaker
        );
    }

    private int convertSpeed(VoiceSettings voiceSettings) {
        if (voiceSettings == null || voiceSettings.getVoiceSpeed() == null) {
            return 0; // Clova 기본값
        }
        // voiceSpeed: 0.5 ~ 2.0 (기본 1.0) → Clova speed: -5 ~ 5 (기본 0)
        double speed = voiceSettings.getVoiceSpeed();
        int clovaSpeed = (int) Math.round((speed - 1.0) * 10);
        return Math.max(-5, Math.min(5, clovaSpeed));
    }

    private String buildFormData(String text, String speaker, int speed) {
        String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
        return String.format("speaker=%s&speed=%d&volume=0&pitch=0&format=mp3&text=%s",
                speaker, speed, encodedText);
    }

    private void validateAudioFile(MultipartFile audioFile) {
        if (audioFile == null || audioFile.isEmpty()) {
            throw new VoiceProcessingException("오디오 파일이 비어있습니다.");
        }

        String contentType = audioFile.getContentType();
        if (contentType == null || !isValidAudioType(contentType)) {
            throw new VoiceProcessingException("지원하지 않는 오디오 형식입니다. (지원 형식: mp3, mp4, mpeg, mpga, m4a, wav, webm)");
        }

        // Whisper API 최대 파일 크기: 25MB
        long maxSize = 25 * 1024 * 1024;
        if (audioFile.getSize() > maxSize) {
            throw new VoiceProcessingException("파일 크기가 25MB를 초과합니다.");
        }
    }

    private boolean isValidAudioType(String contentType) {
        return contentType.equals("audio/mpeg") ||
                contentType.equals("audio/mp3") ||
                contentType.equals("audio/mp4") ||
                contentType.equals("audio/mpga") ||
                contentType.equals("audio/m4a") ||
                contentType.equals("audio/wav") ||
                contentType.equals("audio/webm") ||
                contentType.equals("audio/x-wav") ||
                contentType.equals("audio/wave");
    }
}
