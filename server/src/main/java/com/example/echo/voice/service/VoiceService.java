package com.example.echo.voice.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
// [2024-01 merge] voice.dto.VoiceSettings → user.dto.VoiceSettings로 통일
// 이유: user/dto에 더 완성도 높은 VoiceSettings가 있어 중복 제거
import com.example.echo.user.dto.VoiceSettings;

/**
 * 음성 처리 서비스 인터페이스
 */
public interface VoiceService {
    String speechToText(MultipartFile audioFile);

    /**
     * [STT 환각 방지] 16-bit 모노 PCM이 사실상 빈 오디오(짧고 동시에 조용함)인지 - speechToText의 WAV 판정과 같은 기준.
     * 실시간 전사(/message-live)에서 서버가 중계하며 모은 PCM으로 판정한다.
     */
    boolean isEffectivelySilent(byte[] pcm16le, int sampleRate);

    /**
     * [STT 환각 방지] 실시간 전사 결과 필터 - 알려진 환각 문구이거나 같은 말이 반복되면(compression ratio) 빈 문자열.
     * 실시간 전사는 whisper verbose_json의 세그먼트 신뢰도 지표가 없어 텍스트만으로 판정한다.
     */
    String filterLiveTranscript(String transcript);
    byte[] textToSpeech(String text, VoiceSettings voiceSettings);

    /**
     * 스트리밍 TTS. 첫 오디오 바이트가 도착한 뒤에 반환하며 호출자가 반드시 close() 해야 한다.
     */
    InputStream textToSpeechStream(String text, VoiceSettings voiceSettings);
}
