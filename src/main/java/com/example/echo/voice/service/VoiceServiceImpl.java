/*
STTclient, TTS client 실행하는 곳

 "음성 변환 작업반장"
   * - 파일이 올바른지 검사하고
   * - STT/TTS 클라이언트에게 작업 지시
   * - 결과를 정리해서 반환
*/
package com.example.echo.voice.service;

import com.example.echo.voice.client.STTClient;
import com.example.echo.voice.dto.VoiceSettings;
import com.example.echo.voice.dto.WhisperTranscriptionResponse;
import com.example.echo.voice.exception.VoiceProcessingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class VoiceServiceImpl implements VoiceService {

    private final STTClient sttClient; // 1. STTClient

    @Value("${openai.whisper.model:whisper-1}") // 2. 설정 값들
    private String whisperModel;

    @Value("${openai.whisper.language:ko}")
    private String defaultLanguage;

    @Override
    public String speechToText(MultipartFile audioFile) { // 3. 메인 기능
        validateAudioFile(audioFile);
        //-1 validateAudioFile() 파일 검사/1.파일이 있는지 2.음성 파일인지 3.크기맞는지

        //-2 open Ai한테 물어보기
        try {
            WhisperTranscriptionResponse response = sttClient.transcribe(
                    audioFile,
                    whisperModel,
                    defaultLanguage,
                    "json"
            );

            //-3 답이 제대로 왔는지 확인
            if (response == null || response.getText() == null) {
                throw new VoiceProcessingException("Whisper API 응답이 비어있습니다.");
            }

            //-4 (디버깅 용)기록하고 결과 돌려주기
            log.info("STT 변환 완료: {} bytes -> {} chars",
                    audioFile.getSize(),
                    response.getText().length());

            return response.getText();

        } catch (VoiceProcessingException e) { //에러 처리
            throw e;
        } catch (Exception e) {
            log.error("STT 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new VoiceProcessingException("음성을 텍스트로 변환하는 중 오류가 발생했습니다.", e);
        }
    }

    @Override
    public byte[] textToSpeech(String text, VoiceSettings voiceSettings) {
        // TTS 구현은 추후 작업
        throw new UnsupportedOperationException("TTS 기능은 아직 구현되지 않았습니다.");
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
