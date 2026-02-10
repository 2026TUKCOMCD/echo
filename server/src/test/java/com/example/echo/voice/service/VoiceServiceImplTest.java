package com.example.echo.voice.service;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.client.STTClient;
import com.example.echo.voice.client.TTSClient;
import com.example.echo.voice.dto.WhisperTranscriptionResponse;
import com.example.echo.voice.exception.VoiceProcessingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VoiceServiceImplTest {

    @Mock
    private STTClient sttClient;

    @Mock
    private TTSClient ttsClient;

    private VoiceServiceImpl voiceService;

    @BeforeEach
    void setUp() {
        voiceService = new VoiceServiceImpl(sttClient, ttsClient);
        ReflectionTestUtils.setField(voiceService, "whisperModel", "whisper-1");
        ReflectionTestUtils.setField(voiceService, "defaultLanguage", "ko");
        ReflectionTestUtils.setField(voiceService, "defaultSpeaker", "nara");
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

            when(sttClient.transcribe(any(), eq("whisper-1"), eq("ko"), eq("json")))
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
                    "file", "test.wav", "audio/wav", "fake-wav".getBytes()
            );

            WhisperTranscriptionResponse response = new WhisperTranscriptionResponse();
            ReflectionTestUtils.setField(response, "text", "테스트");

            when(sttClient.transcribe(any(), any(), any(), any())).thenReturn(response);

            String result = voiceService.speechToText(wavFile);

            assertThat(result).isEqualTo("테스트");
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

    // ========== TTS 테스트 ==========

    @Nested
    @DisplayName("textToSpeech - TTS 변환")
    class TextToSpeechTest {

        @Test
        @DisplayName("정상 플로우: 텍스트 + VoiceSettings → 음성 바이트 배열 반환")
        void success() {
            // Given
            VoiceSettings settings = VoiceSettings.builder()
                    .voiceSpeed(1.0)
                    .voiceTone("warm")
                    .build();
            byte[] expectedAudio = "fake-mp3-data".getBytes();

            when(ttsClient.synthesize(any())).thenReturn(expectedAudio);

            // When
            byte[] result = voiceService.textToSpeech("안녕하세요", settings);

            // Then
            assertThat(result).isEqualTo(expectedAudio);
            verify(ttsClient, times(1)).synthesize(any());
        }

        @Test
        @DisplayName("텍스트가 null이면 VoiceProcessingException 발생")
        void nullText_throwsException() {
            assertThatThrownBy(() -> voiceService.textToSpeech(null, null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("변환할 텍스트가 비어있습니다.");
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
        @DisplayName("텍스트가 2000자 초과이면 VoiceProcessingException 발생")
        void textExceedsMaxLength_throwsException() {
            String longText = "가".repeat(2001);

            assertThatThrownBy(() -> voiceService.textToSpeech(longText, null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("텍스트가 2000자를 초과합니다.");
        }

        @Test
        @DisplayName("텍스트가 정확히 2000자이면 정상 처리")
        void textExactly2000chars_success() {
            String maxText = "가".repeat(2000);
            byte[] expectedAudio = "audio".getBytes();

            when(ttsClient.synthesize(any())).thenReturn(expectedAudio);

            byte[] result = voiceService.textToSpeech(maxText, null);

            assertThat(result).isEqualTo(expectedAudio);
        }

        @Test
        @DisplayName("VoiceSettings가 null이면 기본값(nara, speed=0) 사용")
        void nullVoiceSettings_usesDefaults() {
            byte[] expectedAudio = "audio".getBytes();

            when(ttsClient.synthesize(any())).thenReturn(expectedAudio);

            byte[] result = voiceService.textToSpeech("테스트", null);

            assertThat(result).isEqualTo(expectedAudio);
            verify(ttsClient).synthesize(contains("speaker=nara"));
            verify(ttsClient).synthesize(contains("speed=0"));
        }

        @Test
        @DisplayName("voiceTone=warm → speaker=nara")
        void warmTone_resolvesToNara() {
            VoiceSettings settings = VoiceSettings.builder().voiceTone("warm").build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speaker=nara"));
        }

        @Test
        @DisplayName("voiceTone=calm → speaker=vyuna")
        void calmTone_resolvesToVyuna() {
            VoiceSettings settings = VoiceSettings.builder().voiceTone("calm").build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speaker=vyuna"));
        }

        @Test
        @DisplayName("voiceTone=bright → speaker=vdain")
        void brightTone_resolvesToVdain() {
            VoiceSettings settings = VoiceSettings.builder().voiceTone("bright").build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speaker=vdain"));
        }

        @Test
        @DisplayName("voiceTone=gentle → speaker=nminyoung")
        void gentleTone_resolvesToNminyoung() {
            VoiceSettings settings = VoiceSettings.builder().voiceTone("gentle").build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speaker=nminyoung"));
        }

        @Test
        @DisplayName("알 수 없는 voiceTone → 기본 speaker(nara) 사용")
        void unknownTone_usesDefaultSpeaker() {
            VoiceSettings settings = VoiceSettings.builder().voiceTone("unknown").build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speaker=nara"));
        }

        @Test
        @DisplayName("voiceTone이 null이면 기본 speaker 사용")
        void nullTone_usesDefaultSpeaker() {
            VoiceSettings settings = VoiceSettings.builder().voiceTone(null).build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speaker=nara"));
        }

        @Test
        @DisplayName("voiceSpeed=0.5 → clovaSpeed=-5")
        void speed05_convertsToMinus5() {
            VoiceSettings settings = VoiceSettings.builder().voiceSpeed(0.5).build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speed=-5"));
        }

        @Test
        @DisplayName("voiceSpeed=1.0 → clovaSpeed=0")
        void speed10_convertsTo0() {
            VoiceSettings settings = VoiceSettings.builder().voiceSpeed(1.0).build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speed=0"));
        }

        @Test
        @DisplayName("voiceSpeed=1.5 → clovaSpeed=5")
        void speed15_convertsTo5() {
            VoiceSettings settings = VoiceSettings.builder().voiceSpeed(1.5).build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speed=5"));
        }

        @Test
        @DisplayName("voiceSpeed=2.0 → clovaSpeed=5 (최댓값 제한)")
        void speed20_clampedTo5() {
            VoiceSettings settings = VoiceSettings.builder().voiceSpeed(2.0).build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speed=5"));
        }

        @Test
        @DisplayName("voiceSpeed가 null이면 clovaSpeed=0 사용")
        void nullSpeed_convertsTo0() {
            VoiceSettings settings = VoiceSettings.builder().voiceSpeed(null).build();
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", settings);

            verify(ttsClient).synthesize(contains("speed=0"));
        }

        @Test
        @DisplayName("Clova TTS API 응답이 null이면 VoiceProcessingException 발생")
        void nullApiResponse_throwsException() {
            when(ttsClient.synthesize(any())).thenReturn(null);

            assertThatThrownBy(() -> voiceService.textToSpeech("테스트", null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("Clova TTS API 응답이 비어있습니다.");
        }

        @Test
        @DisplayName("Clova TTS API 응답이 빈 배열이면 VoiceProcessingException 발생")
        void emptyApiResponse_throwsException() {
            when(ttsClient.synthesize(any())).thenReturn(new byte[0]);

            assertThatThrownBy(() -> voiceService.textToSpeech("테스트", null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("Clova TTS API 응답이 비어있습니다.");
        }

        @Test
        @DisplayName("TTS 클라이언트에서 예외 발생 시 VoiceProcessingException으로 래핑")
        void clientException_wrappedAsVoiceProcessingException() {
            when(ttsClient.synthesize(any()))
                    .thenThrow(new RuntimeException("Clova API 연결 실패"));

            assertThatThrownBy(() -> voiceService.textToSpeech("테스트", null))
                    .isInstanceOf(VoiceProcessingException.class)
                    .hasMessage("텍스트를 음성으로 변환하는 중 오류가 발생했습니다.")
                    .hasCauseInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("formData에 한글 텍스트가 URL 인코딩되어 전달됨")
        void koreanText_urlEncoded() {
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("안녕", null);

            // URL 인코딩된 한글이 포함된 formData로 호출되어야 함
            verify(ttsClient).synthesize(argThat(formData ->
                    formData.contains("text=") && !formData.contains("text=안녕")
            ));
        }

        @Test
        @DisplayName("formData에 format=mp3 포함")
        void formData_containsFormatMp3() {
            when(ttsClient.synthesize(any())).thenReturn("audio".getBytes());

            voiceService.textToSpeech("테스트", null);

            verify(ttsClient).synthesize(contains("format=mp3"));
        }
    }
}
