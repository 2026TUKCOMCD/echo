package com.example.echo.integration;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.dto.TtsRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
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
import org.springframework.test.context.jdbc.Sql;
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
 * - TTS: Naver Clova Voice API
 *
 * 주의: 테스트 실행 시 실제 API 비용이 발생합니다.
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
    @Disabled("Clova API 설정 완료 후 활성화 필요")
    @DisplayName("TTS 통합 테스트 - /api/voice/tts")
    class TtsIntegrationTest {

        @Test
        @DisplayName("한국어 텍스트 -> Clova TTS API -> MP3 음성 데이터")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
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
            MvcResult result = mockMvc.perform(post("/api/voice/tts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "audio/mpeg"))
                    .andReturn();

            // Then
            byte[] audioData = result.getResponse().getContentAsByteArray();
            assertThat(audioData).isNotEmpty();
            assertThat(audioData.length).isGreaterThan(1000);

            System.out.println("=== TTS 결과 ===");
            System.out.println("생성된 음성 크기: " + audioData.length + " bytes");
        }

        @Test
        @DisplayName("다양한 voiceTone 설정으로 TTS 변환")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
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

                MvcResult result = mockMvc.perform(post("/api/voice/tts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Content-Type", "audio/mpeg"))
                        .andReturn();

                byte[] audioData = result.getResponse().getContentAsByteArray();
                assertThat(audioData).isNotEmpty();

                System.out.println("voiceTone=" + tone + " -> " + audioData.length + " bytes");
            }
        }

        @Test
        @DisplayName("VoiceSettings 없이 기본값으로 TTS 변환")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void tts_withoutVoiceSettings_shouldUseDefaults() throws Exception {
            // Given
            TtsRequest request = new TtsRequest("기본 설정 테스트", null);

            // When & Then
            MvcResult result = mockMvc.perform(post("/api/voice/tts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "audio/mpeg"))
                    .andReturn();

            byte[] audioData = result.getResponse().getContentAsByteArray();
            assertThat(audioData).isNotEmpty();

            System.out.println("=== 기본 설정 TTS 결과 ===");
            System.out.println("생성된 음성 크기: " + audioData.length + " bytes");
        }

        @Test
        @DisplayName("다양한 속도 설정으로 TTS 변환")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void tts_withDifferentSpeeds_shouldWork() throws Exception {
            double[] speeds = {0.5, 1.0, 1.5, 2.0};

            for (double speed : speeds) {
                TtsRequest request = new TtsRequest(
                        "속도 테스트입니다.",
                        VoiceSettings.builder()
                                .voiceSpeed(speed)
                                .voiceTone("warm")
                                .build()
                );

                MvcResult result = mockMvc.perform(post("/api/voice/tts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                        .andExpect(status().isOk())
                        .andReturn();

                byte[] audioData = result.getResponse().getContentAsByteArray();
                assertThat(audioData).isNotEmpty();

                System.out.println("voiceSpeed=" + speed + " -> " + audioData.length + " bytes");
            }
        }
    }
}
