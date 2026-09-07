package com.example.echo.integration;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.dto.TtsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Voice API 통합 테스트
 *
 * 실제 외부 API 호출:
 * - STT: OpenAI Whisper API
 * - TTS: ElevenLabs TTS API (MP3 반환)
 *
 * 주의: 테스트 실행 시 실제 API 비용(크레딧)이 발생합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@DisplayName("Voice API 통합 테스트")
class VoiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("STT 통합 테스트 - /api/voice/stt")
    class SttIntegrationTest {

        @Test
        @DisplayName("실제 음성 파일 -> Whisper API -> 한국어 텍스트 변환")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void stt_withRealAudioFile_shouldReturnKoreanText() throws Exception {
            // Given - 테스트용 실제 음성 파일 로드
            ClassPathResource audioResource = new ClassPathResource("test-audio/정왕동.m4a");
            byte[] audioBytes = Files.readAllBytes(audioResource.getFile().toPath());

            MockMultipartFile audioFile = new MockMultipartFile(
                    "file",
                    "정왕동.m4a",
                    "audio/m4a",
                    audioBytes
            );

            // When
            MvcResult result = mockMvc.perform(multipart("/api/voice/stt")
                            .file(audioFile))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.text").isNotEmpty())
                    .andReturn();

            // Then
            String responseBody = result.getResponse().getContentAsString();
            System.out.println("=== STT 결과 ===");
            System.out.println(responseBody);

            assertThat(responseBody).contains("text");
        }
    }

    @Nested
    @DisplayName("TTS 통합 테스트 - /api/voice/tts")
    class TtsIntegrationTest {

        @Test
        @DisplayName("한국어 텍스트 -> ElevenLabs TTS API -> MP3 음성 데이터")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void tts_withKoreanText_shouldReturnMp3Audio() throws Exception {
            // Given
            TtsRequest request = new TtsRequest(
                    "안녕하세요, 오늘 하루는 어떠셨어요?",
                    VoiceSettings.builder()
                            .voiceSpeed(1.0)
                            .voiceTone("warm")
                            .build()
            );

            // When
            long start = System.currentTimeMillis();
            MvcResult result = mockMvc.perform(post("/api/voice/tts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "audio/mpeg"))
                    .andReturn();
            long elapsed = System.currentTimeMillis() - start;

            // Then
            byte[] audioData = result.getResponse().getContentAsByteArray();
            assertMp3Format(audioData);

            System.out.printf("=== ElevenLabs TTS 레이턴시: %dms, 크기: %d bytes ===%n",
                    elapsed, audioData.length);
        }

        @Test
        @DisplayName("다양한 voiceTone 설정으로 ElevenLabs TTS 변환")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        void tts_withDifferentVoiceTones_shouldWork() throws Exception {
            String[] tones = {"warm", "calm", "bright", "gentle"};

            for (String tone : tones) {
                TtsRequest request = new TtsRequest(
                        "테스트 메시지입니다.",
                        VoiceSettings.builder()
                                .voiceSpeed(1.0)
                                .voiceTone(tone)
                                .build()
                );

                long start = System.currentTimeMillis();
                MvcResult result = mockMvc.perform(post("/api/voice/tts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "audio/mpeg"))
                        .andReturn();
                long elapsed = System.currentTimeMillis() - start;

                byte[] audioData = result.getResponse().getContentAsByteArray();
                assertMp3Format(audioData);

                System.out.printf("voiceTone=%-6s -> %d bytes, %dms%n", tone, audioData.length, elapsed);
            }
        }

        @Test
        @DisplayName("VoiceSettings 없이 기본값으로 ElevenLabs TTS 변환")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void tts_withoutVoiceSettings_shouldUseDefaults() throws Exception {
            // Given
            TtsRequest request = new TtsRequest("기본 설정 테스트", null);

            // When
            long start = System.currentTimeMillis();
            MvcResult result = mockMvc.perform(post("/api/voice/tts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "audio/mpeg"))
                    .andReturn();
            long elapsed = System.currentTimeMillis() - start;

            // Then
            byte[] audioData = result.getResponse().getContentAsByteArray();
            assertMp3Format(audioData);

            System.out.printf("=== 기본 설정 ElevenLabs TTS: %dms, %d bytes ===%n",
                    elapsed, audioData.length);
        }

        @Test
        @DisplayName("voiceSpeed 설정은 무시되지만 오류 없이 처리됨 (ElevenLabs 미지원 파라미터)")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        void tts_withDifferentSpeeds_shouldStillWork() throws Exception {
            double[] speeds = {0.5, 1.0, 1.5, 2.0};

            for (double speed : speeds) {
                TtsRequest request = new TtsRequest(
                        "속도 테스트입니다.",
                        VoiceSettings.builder()
                                .voiceSpeed(speed)
                                .voiceTone("warm")
                                .build()
                );

                long start = System.currentTimeMillis();
                MvcResult result = mockMvc.perform(post("/api/voice/tts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "audio/mpeg"))
                        .andReturn();
                long elapsed = System.currentTimeMillis() - start;

                byte[] audioData = result.getResponse().getContentAsByteArray();
                assertMp3Format(audioData);

                System.out.printf("voiceSpeed=%.1f -> %d bytes, %dms%n", speed, audioData.length, elapsed);
            }
        }
    }

    private void assertMp3Format(byte[] audioData) {
        assertThat(audioData.length).isGreaterThan(4);
        boolean hasId3Tag = audioData[0] == 'I' && audioData[1] == 'D' && audioData[2] == '3';
        boolean hasFrameSync = (audioData[0] & 0xFF) == 0xFF && (audioData[1] & 0xE0) == 0xE0;
        assertThat(hasId3Tag || hasFrameSync)
                .as("MP3 파일은 ID3 태그 또는 프레임 동기 바이트로 시작해야 합니다.")
                .isTrue();
    }
}
