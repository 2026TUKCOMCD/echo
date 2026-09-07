package com.example.echo.voice.service;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.client.STTClient;
import com.example.echo.voice.dto.WhisperTranscriptionResponse;
import com.example.echo.voice.exception.VoiceProcessingException;
import com.example.echo.voice.provider.TtsProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * VoiceServiceImpl은 STT 오케스트레이션과 TTS 프로바이더 선택/검증만 담당한다.
 * 프로바이더별 합성 로직(SSML 빌드, 재시도 등) 테스트는 AzureTtsProviderTest / ElevenLabsTtsProviderTest 참고.
 */
@ExtendWith(MockitoExtension.class)
class VoiceServiceImplTest {

    @Mock
    private STTClient sttClient;

    @Mock
    private TtsProvider azureProvider;

    @Mock
    private TtsProvider elevenLabsProvider;

    private VoiceServiceImpl voiceService;

    @BeforeEach
    void setUp() {
        lenient().when(azureProvider.getName()).thenReturn("azure");
        lenient().when(elevenLabsProvider.getName()).thenReturn("elevenlabs");

        voiceService = new VoiceServiceImpl(sttClient, List.of(azureProvider, elevenLabsProvider));
        ReflectionTestUtils.setField(voiceService, "whisperModel", "whisper-1");
        ReflectionTestUtils.setField(voiceService, "defaultLanguage", "ko");
        ReflectionTestUtils.setField(voiceService, "ttsProvider", "elevenlabs");
        ReflectionTestUtils.setField(voiceService, "minDurationMs", 300L);
        ReflectionTestUtils.setField(voiceService, "minEnergyDbfs", -45.0);
        ReflectionTestUtils.setField(voiceService, "noSpeechThreshold", 0.6);
        ReflectionTestUtils.setField(voiceService, "logprobThreshold", -1.0);
        ReflectionTestUtils.setField(voiceService, "compressionRatioThreshold", 2.4);
    }

    /** 44바이트 표준 PCM WAV 헤더 + 지정한 진폭의 무음/발화를 흉내낸 PCM 데이터를 생성 */
    private static byte[] buildWavBytes(int sampleRate, int durationMs, short amplitude) {
        int numSamples = sampleRate * durationMs / 1000;
        int dataSize = numSamples * 2; // 16-bit mono
        int byteRate = sampleRate * 2;

        ByteBuffer buffer = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes());
        buffer.putInt(36 + dataSize);
        buffer.put("WAVE".getBytes());
        buffer.put("fmt ".getBytes());
        buffer.putInt(16);
        buffer.putShort((short) 1);
        buffer.putShort((short) 1);
        buffer.putInt(sampleRate);
        buffer.putInt(byteRate);
        buffer.putShort((short) 2);
        buffer.putShort((short) 16);
        buffer.put("data".getBytes());
        buffer.putInt(dataSize);
        for (int i = 0; i < numSamples; i++) {
            buffer.putShort(amplitude);
        }
        return buffer.array();
    }

    private static WhisperTranscriptionResponse.Segment buildSegment(
            double noSpeechProb, double avgLogprob, double compressionRatio) {
        WhisperTranscriptionResponse.Segment segment = new WhisperTranscriptionResponse.Segment();
        ReflectionTestUtils.setField(segment, "noSpeechProb", noSpeechProb);
        ReflectionTestUtils.setField(segment, "avgLogprob", avgLogprob);
        ReflectionTestUtils.setField(segment, "compressionRatio", compressionRatio);
        return segment;
    }

    // ========== STT 테스트 ==========

    @Nested
    @DisplayName("speechToText - STT 변환")
    class SpeechToTextTest {

        @Test
        @DisplayName("정상 플로우: 유효한 음성 파일 → 텍스트 반환")
        void success() {
            // Given
            MockMultipartFile audioFile = new MockMultipartFile(
                    "file", "test.mp3", "audio/mpeg", "fake-audio-data".getBytes()
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "안녕하세요");

            when(sttClient.transcribe(any(), eq("whisper-1"), eq("ko"), eq("verbose_json")))
                    .thenReturn(response);

            // When
            String result = voiceService.speechToText(audioFile);

            // Then
            assertThat(result).isEqualTo("안녕하세요");
            verify(sttClient, times(1)).transcribe(any(), any(), any(), any());
        }

        @Test
        @DisplayName("파일이 null이면 VoiceProcessingException 발생")
        void nullFile_throwsException() {
            assertThatThrownBy(() -> voiceService.speechToText(null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("오디오 파일이 비어있습니다.");
        }

        @Test
        @DisplayName("빈 파일이면 VoiceProcessingException 발생")
        void emptyFile_throwsException() {
            MockMultipartFile emptyFile = new MockMultipartFile(
                    "file", "test.mp3", "audio/mpeg", new byte[0]
            );

            assertThatThrownBy(() -> voiceService.speechToText(emptyFile))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("오디오 파일이 비어있습니다.");
        }

        @Test
        @DisplayName("지원하지 않는 오디오 형식이면 VoiceProcessingException 발생")
        void unsupportedFormat_throwsException() {
            MockMultipartFile pdfFile = new MockMultipartFile(
                    "file", "test.pdf", "application/pdf", "data".getBytes()
            );

            assertThatThrownBy(() -> voiceService.speechToText(pdfFile))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessageContaining("지원하지 않는 오디오 형식입니다.");
        }

        @Test
        @DisplayName("Content-Type이 null이면 VoiceProcessingException 발생")
        void nullContentType_throwsException() {
            MockMultipartFile noTypeFile = new MockMultipartFile(
                    "file", "test.mp3", null, "data".getBytes()
            );

            assertThatThrownBy(() -> voiceService.speechToText(noTypeFile))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessageContaining("지원하지 않는 오디오 형식입니다.");
        }

        @Test
        @DisplayName("25MB 초과 파일이면 VoiceProcessingException 발생")
        void oversizedFile_throwsException() {
            byte[] largeData = new byte[26 * 1024 * 1024];
            MockMultipartFile largeFile = new MockMultipartFile(
                    "file", "large.mp3", "audio/mpeg", largeData
            );

            assertThatThrownBy(() -> voiceService.speechToText(largeFile))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("파일 크기가 25MB를 초과합니다.");
        }

        @Test
        @DisplayName("Whisper API 응답이 null이면 VoiceProcessingException 발생")
        void nullResponse_throwsException() {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "file", "test.mp3", "audio/mpeg", "data".getBytes()
            );

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(null);

            assertThatThrownBy(() -> voiceService.speechToText(audioFile))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("Whisper API 응답이 비어있습니다.");
        }

        @Test
        @DisplayName("Whisper API 응답 텍스트가 null이면 VoiceProcessingException 발생")
        void nullResponseText_throwsException() {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "file", "test.mp3", "audio/mpeg", "data".getBytes()
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            // text 필드 설정하지 않음 → null

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            assertThatThrownBy(() -> voiceService.speechToText(audioFile))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("Whisper API 응답이 비어있습니다.");
        }

        @Test
        @DisplayName("STT 클라이언트에서 예외 발생 시 VoiceProcessingException으로 래핑")
        void clientException_wrappedAsVoiceProcessingException() {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "file", "test.mp3", "audio/mpeg", "data".getBytes()
            );

            when(sttClient.transcribe(any(), any(), any(), any()))
                    .thenThrow(new RuntimeException("API 연결 실패"));

            assertThatThrownBy(() -> voiceService.speechToText(audioFile))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("음성을 텍스트로 변환하는 중 오류가 발생했습니다.")
                    .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("wav 형식 파일도 정상 처리")
        void wavFormat_success() {
            MockMultipartFile wavFile = new MockMultipartFile(
                    "file", "test.wav", "audio/wav", buildWavBytes(16000, 1000, (short) 20000)
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "테스트");

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            String result = voiceService.speechToText(wavFile);

            assertThat(result).isEqualTo("테스트");
        }

        // ===== [STT 환각 방지] 최소 길이/에너지 사전 체크 =====

        @Test
        @DisplayName("짧고 동시에 조용한 WAV(사실상 빈 오디오)는 Whisper 호출 없이 빈 문자열 반환")
        void tooShortAndTooQuietWav_skipsSttCall() {
            MockMultipartFile wavFile = new MockMultipartFile(
                    "file", "test.wav", "audio/wav", buildWavBytes(16000, 100, (short) 0)
            );

            String result = voiceService.speechToText(wavFile);

            assertThat(result).isEmpty();
            verify(sttClient, never()).transcribe(any(), any(), any(), any());
        }

        @Test
        @DisplayName("짧지만 또렷하고 큰 소리인 WAV는 길이만으로 걸러지지 않고 Whisper까지 호출됨")
        void shortButLoudWav_stillCallsStt() {
            MockMultipartFile wavFile = new MockMultipartFile(
                    "file", "test.wav", "audio/wav", buildWavBytes(16000, 100, (short) 20000)
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "네");

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            String result = voiceService.speechToText(wavFile);

            assertThat(result).isEqualTo("네");
            verify(sttClient, times(1)).transcribe(any(), any(), any(), any());
        }

        // ===== [STT 환각 방지] verbose_json 신뢰도 필터링 =====

        @Test
        @DisplayName("no_speech_prob가 높고 avg_logprob가 낮으면 환각으로 판단해 빈 문자열 반환")
        void highNoSpeechProbAndLowLogprob_filteredAsHallucination() {
            MockMultipartFile wavFile = new MockMultipartFile(
                    "file", "test.wav", "audio/wav", buildWavBytes(16000, 1000, (short) 20000)
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "시청해주셔서 감사합니다");
            ReflectionTestUtils.setField(response, "segments", List.of(buildSegment(0.9, -2.0, 1.0)));

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            String result = voiceService.speechToText(wavFile);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("compression_ratio가 높으면(반복 패턴) 환각으로 판단해 빈 문자열 반환")
        void highCompressionRatio_filteredAsHallucination() {
            MockMultipartFile wavFile = new MockMultipartFile(
                    "file", "test.wav", "audio/wav", buildWavBytes(16000, 1000, (short) 20000)
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "네네네네네네네네네네");
            ReflectionTestUtils.setField(response, "segments", List.of(buildSegment(0.1, -0.2, 3.0)));

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            String result = voiceService.speechToText(wavFile);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("신뢰도 지표가 정상 범위면 필터링되지 않고 텍스트 그대로 반환")
        void normalConfidence_notFiltered() {
            MockMultipartFile wavFile = new MockMultipartFile(
                    "file", "test.wav", "audio/wav", buildWavBytes(16000, 1000, (short) 20000)
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "안녕하세요");
            ReflectionTestUtils.setField(response, "segments", List.of(buildSegment(0.05, -0.3, 1.2)));

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            String result = voiceService.speechToText(wavFile);

            assertThat(result).isEqualTo("안녕하세요");
        }

        // ===== [STT 환각 방지] 알려진 환각 문구 백스톱 필터 =====

        @Test
        @DisplayName("알려진 환각 문구와 정확히 일치하면 빈 문자열 반환")
        void knownHallucinationPhrase_filtered() {
            MockMultipartFile wavFile = new MockMultipartFile(
                    "file", "test.wav", "audio/wav", buildWavBytes(16000, 1000, (short) 20000)
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "시청해주셔서 감사합니다.");

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            String result = voiceService.speechToText(wavFile);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("어르신의 정상적인 짧은 인사(감사합니다)는 백스톱 필터에 걸리지 않음")
        void legitimateThanksReply_notFiltered() {
            MockMultipartFile wavFile = new MockMultipartFile(
                    "file", "test.wav", "audio/wav", buildWavBytes(16000, 1000, (short) 20000)
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "감사합니다");

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            String result = voiceService.speechToText(wavFile);

            assertThat(result).isEqualTo("감사합니다");
        }

        @Test
        @DisplayName("webm 형식 파일도 정상 처리")
        void webmFormat_success() {
            MockMultipartFile webmFile = new MockMultipartFile(
                    "file", "test.webm", "audio/webm", "fake-webm".getBytes()
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "테스트");

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            String result = voiceService.speechToText(webmFile);

            assertThat(result).isEqualTo("테스트");
        }
    }

    // ========== TTS 검증/프로바이더 선택 테스트 ==========

    @Nested
    @DisplayName("textToSpeech - 검증 및 프로바이더 선택")
    class TextToSpeechTest {

        @Test
        @DisplayName("텍스트가 null이면 VoiceProcessingException 발생 (프로바이더 호출 안 함)")
        void nullText_throwsException() {
            assertThatThrownBy(() -> voiceService.textToSpeech(null, null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("변환할 텍스트가 비어있습니다.");

            verify(azureProvider, never()).synthesize(any(), any());
            verify(elevenLabsProvider, never()).synthesize(any(), any());
        }

        @Test
        @DisplayName("텍스트가 빈 문자열이면 VoiceProcessingException 발생")
        void emptyText_throwsException() {
            assertThatThrownBy(() -> voiceService.textToSpeech("", null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("변환할 텍스트가 비어있습니다.");
        }

        @Test
        @DisplayName("텍스트가 공백만 있으면 VoiceProcessingException 발생")
        void blankText_throwsException() {
            assertThatThrownBy(() -> voiceService.textToSpeech("   ", null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("변환할 텍스트가 비어있습니다.");
        }

        @Test
        @DisplayName("텍스트가 800자 초과이면 VoiceProcessingException 발생")
        void textExceedsMaxLength_throwsException() {
            String longText = "가".repeat(801);

            assertThatThrownBy(() -> voiceService.textToSpeech(longText, null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("텍스트가 800자를 초과합니다.");
        }

        @Test
        @DisplayName("텍스트가 정확히 800자이면 정상 처리")
        void textExactly800chars_success() {
            String maxText = "가".repeat(800);
            byte[] expectedAudio = "audio".getBytes();
            when(elevenLabsProvider.synthesize(eq(maxText), any())).thenReturn(expectedAudio);

            byte[] result = voiceService.textToSpeech(maxText, null);

            assertThat(result).isEqualTo(expectedAudio);
        }

        @Test
        @DisplayName("tts.provider=elevenlabs → ElevenLabs 프로바이더 호출")
        void elevenlabsProvider_selected() {
            VoiceSettings settings = VoiceSettings.builder().voiceTone("warm").build();
            byte[] expectedAudio = "audio".getBytes();
            when(elevenLabsProvider.synthesize("안녕", settings)).thenReturn(expectedAudio);

            byte[] result = voiceService.textToSpeech("안녕", settings);

            assertThat(result).isEqualTo(expectedAudio);
            verify(elevenLabsProvider, times(1)).synthesize("안녕", settings);
            verify(azureProvider, never()).synthesize(any(), any());
        }

        @Test
        @DisplayName("tts.provider=azure → Azure 프로바이더 호출")
        void azureProvider_selected() {
            ReflectionTestUtils.setField(voiceService, "ttsProvider", "azure");
            byte[] expectedAudio = "audio".getBytes();
            when(azureProvider.synthesize(eq("안녕"), any())).thenReturn(expectedAudio);

            byte[] result = voiceService.textToSpeech("안녕", null);

            assertThat(result).isEqualTo(expectedAudio);
            verify(azureProvider, times(1)).synthesize(eq("안녕"), any());
            verify(elevenLabsProvider, never()).synthesize(any(), any());
        }

        @Test
        @DisplayName("인식할 수 없는 tts.provider 값이면 azure로 폴백")
        void unknownProvider_fallsBackToAzure() {
            ReflectionTestUtils.setField(voiceService, "ttsProvider", "unknown-provider");
            byte[] expectedAudio = "audio".getBytes();
            when(azureProvider.synthesize(eq("안녕"), any())).thenReturn(expectedAudio);

            byte[] result = voiceService.textToSpeech("안녕", null);

            assertThat(result).isEqualTo(expectedAudio);
            verify(azureProvider, times(1)).synthesize(eq("안녕"), any());
            verify(elevenLabsProvider, never()).synthesize(any(), any());
        }

        @Test
        @DisplayName("프로바이더가 VoiceProcessingException을 던지면 그대로 전파")
        void providerVoiceProcessingException_propagates() {
            when(elevenLabsProvider.synthesize(any(), any()))
                    .thenThrow(new VoiceProcessingException("ElevenLabs TTS API 응답이 비어있습니다."));

            assertThatThrownBy(() -> voiceService.textToSpeech("안녕", null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("ElevenLabs TTS API 응답이 비어있습니다.");
        }

        @Test
        @DisplayName("프로바이더에서 예상치 못한 예외 발생 시 VoiceProcessingException으로 래핑")
        void providerUnexpectedException_wrapped() {
            when(elevenLabsProvider.synthesize(any(), any()))
                    .thenThrow(new RuntimeException("연결 실패"));

            assertThatThrownBy(() -> voiceService.textToSpeech("안녕", null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("텍스트를 음성으로 변환하는 중 오류가 발생했습니다.")
                    .hasCauseInstanceOf(RuntimeException.class);
        }
    }
}
