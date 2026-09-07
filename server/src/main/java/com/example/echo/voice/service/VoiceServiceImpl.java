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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // [STT 환각 방지] 오디오가 이보다 짧고(ms) 동시에 이보다 조용하면(dBFS) 사실상 빈 오디오로 보고 Whisper 호출을 건너뜀
    @Value("${openai.whisper.min-duration-ms:300}")
    private long minDurationMs;

    @Value("${openai.whisper.min-energy-dbfs:-45.0}")
    private double minEnergyDbfs;

    // [STT 환각 방지] Whisper 공식 CLI가 자체 디코딩에서 쓰는 기본 임계값과 동일
    @Value("${openai.whisper.no-speech-threshold:0.6}")
    private double noSpeechThreshold;

    @Value("${openai.whisper.logprob-threshold:-1.0}")
    private double logprobThreshold;

    @Value("${openai.whisper.compression-ratio-threshold:2.4}")
    private double compressionRatioThreshold;

    // [STT 환각 방지] Whisper가 정보량 적은 오디오에서 유튜브 자막체로 수렴하는, 잘 알려진 환각 문구 백스톱 필터
    private static final Set<String> KNOWN_HALLUCINATION_PHRASES = Set.of(
            "시청해주셔서 감사합니다",
            "구독과 좋아요 부탁드립니다",
            "구독과 좋아요 눌러주세요",
            "다음 영상에서 만나요",
            "다음 시간에 만나요",
            "이 영상이 도움이 되셨다면 구독과 좋아요 부탁드립니다"
    );

    private static final int WAV_HEADER_SIZE = 44;

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

        // 2. [STT 환각 방지] 사실상 빈 오디오(짧고 동시에 조용함)면 Whisper 호출 자체를 건너뜀
        if (isEffectivelySilent(audioFile)) {
            log.info("오디오 길이/에너지 기준 미달로 STT 호출을 건너뜀 - {} bytes", audioFile.getSize());
            return "";
        }

        try {
            // 3. API 호출 (신뢰도 지표를 받기 위해 verbose_json 사용)
            WhisperTranscriptionResponse response = sttClient.transcribe(
                    audioFile,
                    whisperModel,
                    defaultLanguage,
                    "verbose_json"
            );

            // 4. 응답 확인
            if (response == null || response.getText() == null) {
                throw new VoiceProcessingException("Whisper API 응답이 비어있습니다.");
            }

            // 5. [STT 환각 방지] Whisper 자체 신뢰도 지표로 필터링
            if (isLowConfidence(response)) {
                return "";
            }

            // 6. [STT 환각 방지] 알려진 환각 문구 백스톱 필터
            String text = response.getText();
            if (isKnownHallucinationPhrase(text)) {
                log.info("알려진 STT 환각 문구 감지 - 필터링: {}", text);
                return "";
            }

            log.info("STT 변환 완료: {} bytes -> {} chars", audioFile.getSize(), text.length());

            return text;

        } catch (VoiceProcessingException e) {
            throw e;
        } catch (Exception e) {
            log.error("STT 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new VoiceProcessingException("음성을 텍스트로 변환하는 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * [STT 환각 방지] 오디오가 최소 길이(minDurationMs) 미만이면서 동시에
     * 최소 에너지(minEnergyDbfs) 미만이면 사실상 빈 오디오로 판정한다.
     * AND 조건인 이유: "네!"처럼 짧지만 또렷한 정상 발화까지 길이만으로 걸러지는 걸 방지하기 위함.
     * WAV 포맷일 때만 판정하며(헤더 구조가 달라 오탐 방지), 그 외 포맷은 그대로 통과시킨다.
     */
    private boolean isEffectivelySilent(MultipartFile audioFile) {
        String contentType = audioFile.getContentType();
        if (contentType == null || !isWavContentType(contentType)) {
            return false;
        }

        try {
            byte[] bytes = audioFile.getBytes();
            if (bytes.length <= WAV_HEADER_SIZE) {
                return true;
            }

            ByteBuffer header = ByteBuffer.wrap(bytes, 0, WAV_HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
            int sampleRate = header.getInt(24);
            short bitsPerSample = header.getShort(34);
            if (sampleRate <= 0 || bitsPerSample != 16) {
                return false; // 예상 못한 포맷 - 판단하지 않고 통과
            }

            int dataSize = bytes.length - WAV_HEADER_SIZE;
            double durationMs = (dataSize / 2.0) / sampleRate * 1000;
            double energyDbfs = calculateRmsDbfs(bytes, WAV_HEADER_SIZE, bytes.length);

            return durationMs < minDurationMs && energyDbfs < minEnergyDbfs;
        } catch (IOException e) {
            log.warn("오디오 길이/에너지 계산 실패 - 필터링 없이 진행: {}", e.getMessage());
            return false;
        }
    }

    private double calculateRmsDbfs(byte[] bytes, int start, int end) {
        long sumOfSquares = 0;
        int sampleCount = 0;
        for (int i = start; i + 1 < end; i += 2) {
            short sample = (short) ((bytes[i + 1] << 8) | (bytes[i] & 0xFF));
            sumOfSquares += (long) sample * sample;
            sampleCount++;
        }
        if (sampleCount == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        double rms = Math.sqrt((double) sumOfSquares / sampleCount);
        if (rms <= 0.0) {
            return Double.NEGATIVE_INFINITY;
        }
        return 20.0 * Math.log10(rms / 32768.0);
    }

    private boolean isWavContentType(String contentType) {
        return contentType.equals("audio/wav") ||
                contentType.equals("audio/x-wav") ||
                contentType.equals("audio/wave");
    }

    /**
     * [STT 환각 방지] Whisper 공식 CLI가 자체 디코딩에서 쓰는 기본 임계값과 동일한 기준으로,
     * verbose_json 응답의 세그먼트별 신뢰도 지표를 종합해 환각 여부를 판정한다.
     */
    private boolean isLowConfidence(WhisperTranscriptionResponse response) {
        List<WhisperTranscriptionResponse.Segment> segments = response.getSegments();
        if (segments == null || segments.isEmpty()) {
            return false;
        }

        double maxNoSpeechProb = segments.stream()
                .mapToDouble(WhisperTranscriptionResponse.Segment::getNoSpeechProb)
                .max().orElse(0.0);
        double minAvgLogprob = segments.stream()
                .mapToDouble(WhisperTranscriptionResponse.Segment::getAvgLogprob)
                .min().orElse(0.0);
        double maxCompressionRatio = segments.stream()
                .mapToDouble(WhisperTranscriptionResponse.Segment::getCompressionRatio)
                .max().orElse(0.0);

        boolean noSpeechAndLowConfidence = maxNoSpeechProb > noSpeechThreshold && minAvgLogprob < logprobThreshold;
        boolean repetitive = maxCompressionRatio > compressionRatioThreshold;

        if (noSpeechAndLowConfidence || repetitive) {
            log.info("STT 신뢰도 필터링 발동 - no_speech_prob: {}, avg_logprob: {}, compression_ratio: {}",
                    maxNoSpeechProb, minAvgLogprob, maxCompressionRatio);
            return true;
        }
        return false;
    }

    private boolean isKnownHallucinationPhrase(String text) {
        String normalized = text.strip().replaceAll("[.!?~,\\s]+$", "");
        return KNOWN_HALLUCINATION_PHRASES.contains(normalized);
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
