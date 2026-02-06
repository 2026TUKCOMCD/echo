package com.example.echo.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.Disabled;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 대화 흐름 End-to-End 통합 테스트
 *
 * 실제 외부 API 호출 (OpenAI Whisper, GPT-4o-mini, Clova TTS)
 * Local MySQL 사용 (echo_test_db)
 *
 * 주의: 테스트 실행 시 실제 API 비용이 발생합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@Sql(scripts = "/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("대화 흐름 E2E 통합 테스트")
class ConversationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Order(1)
    @DisplayName("1. 대화 시작 - /api/conversations/start")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void startConversation_shouldReturnGreetingWithAudio() throws Exception {
        // When
        MvcResult result = mockMvc.perform(post("/api/conversations/start")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.audioData").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andReturn();

        // Then
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("=== 대화 시작 응답 ===");
        System.out.println("응답: " + responseBody.substring(0, Math.min(200, responseBody.length())) + "...");

        assertThat(responseBody).contains("message");
        assertThat(responseBody).contains("audioData");
    }

    @Test
    @Order(2)
    @DisplayName("2. 사용자 메시지 처리 - /api/conversations/message")
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void processMessage_withRealAudioFile_shouldReturnAIResponse() throws Exception {
        // Given - 먼저 대화 시작
        mockMvc.perform(post("/api/conversations/start"))
                .andExpect(status().isOk());

        // Given - 테스트용 실제 음성 파일 로드
        ClassPathResource audioResource = new ClassPathResource("test-audio/정왕동.m4a");
        byte[] audioBytes = Files.readAllBytes(audioResource.getFile().toPath());

        MockMultipartFile audioFile = new MockMultipartFile(
                "audio",
                "정왕동.m4a",
                "audio/m4a",
                audioBytes
        );

        // When
        MvcResult result = mockMvc.perform(multipart("/api/conversations/message")
                        .file(audioFile))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userMessage").isNotEmpty())
                .andExpect(jsonPath("$.aiResponse").isNotEmpty())
                .andExpect(jsonPath("$.audioData").isNotEmpty())
                .andReturn();

        // Then
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("=== 메시지 처리 응답 ===");
        System.out.println("응답: " + responseBody.substring(0, Math.min(300, responseBody.length())) + "...");

        assertThat(responseBody).contains("userMessage");
        assertThat(responseBody).contains("aiResponse");
    }

    @Test
    @Order(3)
    @DisplayName("3. 대화 종료 - /api/conversations/end")
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void endConversation_shouldReturnEndTime() throws Exception {
        // Given - 먼저 대화 시작
        mockMvc.perform(post("/api/conversations/start"))
                .andExpect(status().isOk());

        // When
        MvcResult result = mockMvc.perform(post("/api/conversations/end")
                        .contentType(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endedAt").isNotEmpty())
                .andReturn();

        // Then
        String responseBody = result.getResponse().getContentAsString();
        System.out.println("=== 대화 종료 응답 ===");
        System.out.println(responseBody);

        assertThat(responseBody).contains("endedAt");
    }

    @Nested
    @DisplayName("전체 대화 시나리오 테스트")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FullConversationScenarioTest {

        @Test
        @Order(1)
        @DisplayName("시나리오: 대화 시작 -> 2턴 대화 -> 종료")
        @Timeout(value = 180, unit = TimeUnit.SECONDS)
        void fullConversationScenario() throws Exception {
            System.out.println("\n========================================");
            System.out.println("전체 대화 시나리오 테스트 시작");
            System.out.println("========================================\n");

            // === 1단계: 대화 시작 ===
            System.out.println("[1단계] 대화 시작...");
            MvcResult startResult = mockMvc.perform(post("/api/conversations/start"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andReturn();

            String startResponse = startResult.getResponse().getContentAsString();
            System.out.println("AI 첫 인사: " + extractMessage(startResponse));

            // === 2단계: 첫 번째 사용자 메시지 ===
            System.out.println("\n[2단계] 첫 번째 사용자 메시지 처리...");
            ClassPathResource audio1 = new ClassPathResource("test-audio/정왕동.m4a");
            MockMultipartFile file1 = new MockMultipartFile(
                    "audio", "turn1.m4a", "audio/m4a",
                    Files.readAllBytes(audio1.getFile().toPath())
            );

            MvcResult turn1Result = mockMvc.perform(multipart("/api/conversations/message").file(file1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userMessage").isNotEmpty())
                    .andExpect(jsonPath("$.aiResponse").isNotEmpty())
                    .andReturn();

            String turn1Response = turn1Result.getResponse().getContentAsString();
            System.out.println("사용자 발화(STT): " + extractField(turn1Response, "userMessage"));
            System.out.println("AI 응답: " + extractField(turn1Response, "aiResponse"));

            // === 3단계: 두 번째 사용자 메시지 ===
            System.out.println("\n[3단계] 두 번째 사용자 메시지 처리...");
            MockMultipartFile file2 = new MockMultipartFile(
                    "audio", "turn2.m4a", "audio/m4a",
                    Files.readAllBytes(audio1.getFile().toPath())
            );

            MvcResult turn2Result = mockMvc.perform(multipart("/api/conversations/message").file(file2))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.aiResponse").isNotEmpty())
                    .andReturn();

            String turn2Response = turn2Result.getResponse().getContentAsString();
            System.out.println("사용자 발화(STT): " + extractField(turn2Response, "userMessage"));
            System.out.println("AI 응답: " + extractField(turn2Response, "aiResponse"));

            // === 4단계: 대화 종료 ===
            System.out.println("\n[4단계] 대화 종료...");
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.endedAt").isNotEmpty());

            System.out.println("\n========================================");
            System.out.println("전체 대화 시나리오 테스트 완료!");
            System.out.println("========================================\n");
        }

        private String extractMessage(String json) {
            try {
                return json.contains("message") ?
                        json.split("\"message\":\"")[1].split("\"")[0] : "파싱 실패";
            } catch (Exception e) {
                return "파싱 실패";
            }
        }

        private String extractField(String json, String field) {
            try {
                if (json.contains(field)) {
                    String[] parts = json.split("\"" + field + "\":\"");
                    if (parts.length > 1) {
                        return parts[1].split("\"")[0];
                    }
                }
                return "파싱 실패";
            } catch (Exception e) {
                return "파싱 실패";
            }
        }
    }

    @Nested
    @DisplayName("예외 케이스 테스트")
    class ExceptionCaseTest {

        @Test
        @DisplayName("대화 시작 없이 메시지 전송 시 에러")
        @Timeout(value = 30, unit = TimeUnit.SECONDS)
        void processMessage_withoutStart_shouldFail() throws Exception {
            // Given - 대화 시작 없이 바로 메시지 전송
            ClassPathResource audioResource = new ClassPathResource("test-audio/정왕동.m4a");
            byte[] audioBytes = Files.readAllBytes(audioResource.getFile().toPath());

            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "test.m4a",
                    "audio/m4a",
                    audioBytes
            );

            // When & Then - 컨텍스트가 없으므로 IllegalStateException 발생 예상
            assertThatThrownBy(() ->
                    mockMvc.perform(multipart("/api/conversations/message")
                            .file(audioFile))
            ).hasCauseInstanceOf(IllegalStateException.class);
        }
    }
}
