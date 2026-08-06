/*
STTclient, TTS 프로바이더 실행하는 곳

 "음성 변환 작업반장"
   * - 파일이 올바른지 검사하고
   * - STT 클라이언트/TTS 프로바이더에게 작업 지시
   * - 결과를 정리해서 반환
*/
package com.example.echo.voice.service;

import com.example.echo.voice.client.STTClient;
// [2024-01 merge] voice.dto.VoiceSettings → user.dto.VoiceSettings로 통일
// 이유: user/dto에 더 완성도 높은 VoiceSettings가 있어 중복 제거
import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.dto.WhisperTranscriptionResponse;
import com.example.echo.voice.exception.VoiceProcessingException;
import com.example.echo.voice.provider.TtsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class VoiceServiceImpl implements VoiceService {

    private final STTClient sttClient;
    private final Map<String, TtsProvider> ttsProviders;
    private final TtsProvider defaultTtsProvider;

    @Value("${openai.whisper.model:whisper-1}")
    private String whisperModel;

    @Value("${openai.whisper.language:ko}")
    private String defaultLanguage;

    @Value("${tts.provider:elevenlabs}")
    private String ttsProvider;

    public VoiceServiceImpl(STTClient sttClient, List<TtsProvider> ttsProviders) {
        this.sttClient = sttClient;
        this.ttsProviders = ttsProviders.stream()
                .collect(Collectors.toMap(TtsProvider::getName, Function.identity()));
        this.defaultTtsProvider = this.ttsProviders.get("azure");
    }

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
     * 2. 검증: validateText() - 빈값/글자수 확인 (800자 제한)
     * 3. tts.provider 설정값으로 TtsProvider 선택 (미인식 값은 azure로 폴백)
     * 4. 선택된 프로바이더에 합성 위임
     * 5. 출력: byte[] (음성 파일)
     */
    @Override
    public byte[] textToSpeech(String text, VoiceSettings voiceSettings) {
        validateText(text);

        try {
            return resolveProvider().synthesize(text, voiceSettings);
        } catch (VoiceProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("TTS 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new VoiceProcessingException("텍스트를 음성으로 변환하는 중 오류가 발생했습니다.", e);
        }
    }

    private TtsProvider resolveProvider() {
        TtsProvider provider = ttsProviders.get(ttsProvider);
        if (provider == null) {
            log.warn("알 수 없는 tts.provider 값 '{}' — azure로 폴백합니다.", ttsProvider);
            return defaultTtsProvider;
        }
        return provider;
    }

    private void validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new VoiceProcessingException("변환할 텍스트가 비어있습니다.");
        }
        // Azure SSML 제한: 800자 (한글 3바이트/자 고려)
        if (text.length() > 800) {
            throw new VoiceProcessingException("텍스트가 800자를 초과합니다.");
        }
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
