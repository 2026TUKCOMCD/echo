package com.example.echo.integration;

import com.example.echo.voice.client.STTClient;
import com.example.echo.voice.dto.WhisperTranscriptionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 텍스트 기반 대화 시나리오 통합 테스트
 *
 * STT만 Mock하여 다양한 사용자 발화를 시뮬레이션
 * AI와 TTS는 실제 API 호출 (자연스러운 대화 흐름 검증)
 *
 * 목적: 실제 대화처럼 다양한 사용자 응답에 따른 AI 반응 확인
 *
 * 주의: AI/TTS API 비용이 발생합니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@Sql(scripts = "/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("텍스트 기반 대화 시나리오 테스트 (STT Mock)")
class TextBasedConversationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private STTClient sttClient;

    /**
     * Mock STT 응답 생성 헬퍼
     */
    private WhisperTranscriptionResponse createMockSttResponse(String text) throws Exception {
        String json = String.format("{\"text\":\"%s\"}", text);
        return objectMapper.readValue(json, WhisperTranscriptionResponse.class);
    }

    @Nested
    @DisplayName("자연스러운 대화 시나리오")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class NaturalConversationScenarioTest {

        @Test
        @Order(1)
        @DisplayName("시나리오: 기분 좋은 하루 대화 (3턴)")
        @Timeout(value = 180, unit = TimeUnit.SECONDS)
        void happyDayConversation() throws Exception {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("시나리오: 기분 좋은 하루 대화");
            System.out.println("=".repeat(60) + "\n");

            // STT Mock 설정 - AI의 context 기반 질문에 대한 응답
            // AI가 건강 데이터(5,000보, 7시간 수면)와 날씨(맑음, 20도)를 활용해 질문
            when(sttClient.transcribe(any(), any(), any(), any()))
                    .thenReturn(createMockSttResponse("네, 아침에 공원에서 산책했어요"))
                    .thenReturn(createMockSttResponse("날씨가 좋아서 기분이 좋았어요"))
                    .thenReturn(createMockSttResponse("네, 대화 즐거웠어요"));

            // 더미 음성 파일 (STT가 Mock되므로 내용은 중요하지 않음)
            ClassPathResource audio = new ClassPathResource("test-audio/정왕동.m4a");
            byte[] audioBytes = Files.readAllBytes(audio.getFile().toPath());

            // === 1단계: 대화 시작 ===
            System.out.println("[1단계] 대화 시작");
            MvcResult startResult = mockMvc.perform(post("/api/conversations/start"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andReturn();

            String aiGreeting = extractField(startResult.getResponse().getContentAsString(), "message");
            System.out.println("AI: " + aiGreeting);

            // === 2단계: 첫 번째 턴 - AI가 걸음 수 기반 질문, 사용자가 답변 ===
            System.out.println("\n[2단계] 첫 번째 턴 (걸음 수 관련)");
            MockMultipartFile file1 = new MockMultipartFile(
                    "audio", "turn1.m4a", "audio/m4a", audioBytes);

            MvcResult turn1Result = mockMvc.perform(multipart("/api/conversations/message").file(file1))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userMessage").isNotEmpty())
                    .andExpect(jsonPath("$.aiResponse").isNotEmpty())
                    .andReturn();

            String turn1Response = turn1Result.getResponse().getContentAsString();
            System.out.println("사용자: " + extractField(turn1Response, "userMessage"));
            System.out.println("AI: " + extractField(turn1Response, "aiResponse"));

            // === 3단계: 두 번째 턴 - AI가 날씨/기분 후속 질문 ===
            System.out.println("\n[3단계] 두 번째 턴 (날씨/기분 관련)");
            MockMultipartFile file2 = new MockMultipartFile(
                    "audio", "turn2.m4a", "audio/m4a", audioBytes);

            MvcResult turn2Result = mockMvc.perform(multipart("/api/conversations/message").file(file2))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userMessage").isNotEmpty())
                    .andExpect(jsonPath("$.aiResponse").isNotEmpty())
                    .andReturn();

            String turn2Response = turn2Result.getResponse().getContentAsString();
            System.out.println("사용자: " + extractField(turn2Response, "userMessage"));
            System.out.println("AI: " + extractField(turn2Response, "aiResponse"));

            // === 4단계: 세 번째 턴 - 대화 마무리 ===
            System.out.println("\n[4단계] 세 번째 턴 (마무리)");
            MockMultipartFile file3 = new MockMultipartFile(
                    "audio", "turn3.m4a", "audio/m4a", audioBytes);

            MvcResult turn3Result = mockMvc.perform(multipart("/api/conversations/message").file(file3))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userMessage").isNotEmpty())
                    .andExpect(jsonPath("$.aiResponse").isNotEmpty())
                    .andReturn();

            String turn3Response = turn3Result.getResponse().getContentAsString();
            System.out.println("사용자: " + extractField(turn3Response, "userMessage"));
            System.out.println("AI: " + extractField(turn3Response, "aiResponse"));

            // === 5단계: 대화 종료 ===
            System.out.println("\n[5단계] 대화 종료");
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.endedAt").isNotEmpty());

            System.out.println("\n" + "=".repeat(60));
            System.out.println("시나리오 완료: 기분 좋은 하루 대화");
            System.out.println("=".repeat(60) + "\n");
        }

        @Test
        @Order(2)
        @DisplayName("시나리오: 건강 관련 대화 (3턴)")
        @Timeout(value = 180, unit = TimeUnit.SECONDS)
        void healthConversation() throws Exception {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("시나리오: 건강 관련 대화");
            System.out.println("=".repeat(60) + "\n");

            // STT Mock 설정 - AI가 수면 데이터(7시간) 기반 질문에 대한 응답
            when(sttClient.transcribe(any(), any(), any(), any()))
                    .thenReturn(createMockSttResponse("아니요, 조금 피곤해요"))
                    .thenReturn(createMockSttResponse("중간에 몇 번 깼어요"))
                    .thenReturn(createMockSttResponse("네, 오늘은 일찍 자볼게요"));

            ClassPathResource audio = new ClassPathResource("test-audio/정왕동.m4a");
            byte[] audioBytes = Files.readAllBytes(audio.getFile().toPath());

            // === 대화 시작 ===
            System.out.println("[1단계] 대화 시작");
            MvcResult startResult = mockMvc.perform(post("/api/conversations/start"))
                    .andExpect(status().isOk())
                    .andReturn();
            System.out.println("AI: " + extractField(startResult.getResponse().getContentAsString(), "message"));

            // === 첫 번째 턴 - AI가 수면 질문, 사용자가 피곤함 표현 ===
            System.out.println("\n[2단계] 첫 번째 턴 (수면 관련)");
            MockMultipartFile file1 = new MockMultipartFile("audio", "turn1.m4a", "audio/m4a", audioBytes);
            MvcResult turn1Result = mockMvc.perform(multipart("/api/conversations/message").file(file1))
                    .andExpect(status().isOk())
                    .andReturn();
            String turn1Response = turn1Result.getResponse().getContentAsString();
            System.out.println("사용자: " + extractField(turn1Response, "userMessage"));
            System.out.println("AI: " + extractField(turn1Response, "aiResponse"));

            // === 두 번째 턴 - AI가 이유 질문, 사용자가 설명 ===
            System.out.println("\n[3단계] 두 번째 턴 (이유 설명)");
            MockMultipartFile file2 = new MockMultipartFile("audio", "turn2.m4a", "audio/m4a", audioBytes);
            MvcResult turn2Result = mockMvc.perform(multipart("/api/conversations/message").file(file2))
                    .andExpect(status().isOk())
                    .andReturn();
            String turn2Response = turn2Result.getResponse().getContentAsString();
            System.out.println("사용자: " + extractField(turn2Response, "userMessage"));
            System.out.println("AI: " + extractField(turn2Response, "aiResponse"));

            // === 세 번째 턴 - AI 조언에 응답 ===
            System.out.println("\n[4단계] 세 번째 턴 - AI 조언에 응답");
            MockMultipartFile file3 = new MockMultipartFile("audio", "turn3.m4a", "audio/m4a", audioBytes);
            MvcResult turn3Result = mockMvc.perform(multipart("/api/conversations/message").file(file3))
                    .andExpect(status().isOk())
                    .andReturn();
            String turn3Response = turn3Result.getResponse().getContentAsString();
            System.out.println("사용자: " + extractField(turn3Response, "userMessage"));
            System.out.println("AI: " + extractField(turn3Response, "aiResponse"));

            // === 대화 종료 ===
            System.out.println("\n[5단계] 대화 종료");
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk());

            System.out.println("\n" + "=".repeat(60));
            System.out.println("시나리오 완료: 건강 관련 대화");
            System.out.println("=".repeat(60) + "\n");
        }

        @Test
        @Order(3)
        @DisplayName("시나리오: 추억 회상 대화 (3턴)")
        @Timeout(value = 180, unit = TimeUnit.SECONDS)
        void memoryConversation() throws Exception {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("시나리오: 추억 회상 대화");
            System.out.println("=".repeat(60) + "\n");

            // STT Mock 설정 - AI가 산책/날씨 관련 질문하면 추억으로 연결
            when(sttClient.transcribe(any(), any(), any(), any()))
                    .thenReturn(createMockSttResponse("네, 산책하면 옛날 생각이 나요"))
                    .thenReturn(createMockSttResponse("젊었을 때 고향에서 농사를 지었어요"))
                    .thenReturn(createMockSttResponse("그때가 참 좋았어요"));

            ClassPathResource audio = new ClassPathResource("test-audio/정왕동.m4a");
            byte[] audioBytes = Files.readAllBytes(audio.getFile().toPath());

            // === 대화 시작 ===
            System.out.println("[1단계] 대화 시작");
            MvcResult startResult = mockMvc.perform(post("/api/conversations/start"))
                    .andExpect(status().isOk())
                    .andReturn();
            System.out.println("AI: " + extractField(startResult.getResponse().getContentAsString(), "message"));

            // === 첫 번째 턴 - AI가 산책 질문, 사용자가 추억 연결 ===
            System.out.println("\n[2단계] 첫 번째 턴 (추억 연결)");
            MockMultipartFile file1 = new MockMultipartFile("audio", "turn1.m4a", "audio/m4a", audioBytes);
            MvcResult turn1Result = mockMvc.perform(multipart("/api/conversations/message").file(file1))
                    .andExpect(status().isOk())
                    .andReturn();
            String turn1Response = turn1Result.getResponse().getContentAsString();
            System.out.println("사용자: " + extractField(turn1Response, "userMessage"));
            System.out.println("AI: " + extractField(turn1Response, "aiResponse"));

            // === 두 번째 턴 - 과거 이야기 ===
            System.out.println("\n[3단계] 두 번째 턴 - 과거 이야기");
            MockMultipartFile file2 = new MockMultipartFile("audio", "turn2.m4a", "audio/m4a", audioBytes);
            MvcResult turn2Result = mockMvc.perform(multipart("/api/conversations/message").file(file2))
                    .andExpect(status().isOk())
                    .andReturn();
            String turn2Response = turn2Result.getResponse().getContentAsString();
            System.out.println("사용자: " + extractField(turn2Response, "userMessage"));
            System.out.println("AI: " + extractField(turn2Response, "aiResponse"));

            // === 세 번째 턴 - 감상 공유 ===
            System.out.println("\n[4단계] 세 번째 턴 - 감상 공유");
            MockMultipartFile file3 = new MockMultipartFile("audio", "turn3.m4a", "audio/m4a", audioBytes);
            MvcResult turn3Result = mockMvc.perform(multipart("/api/conversations/message").file(file3))
                    .andExpect(status().isOk())
                    .andReturn();
            String turn3Response = turn3Result.getResponse().getContentAsString();
            System.out.println("사용자: " + extractField(turn3Response, "userMessage"));
            System.out.println("AI: " + extractField(turn3Response, "aiResponse"));

            // === 대화 종료 ===
            System.out.println("\n[5단계] 대화 종료");
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk());

            System.out.println("\n" + "=".repeat(60));
            System.out.println("시나리오 완료: 추억 회상 대화");
            System.out.println("=".repeat(60) + "\n");
        }
    }

    /**
     * JSON에서 특정 필드 값 추출
     */
    private String extractField(String json, String field) {
        try {
            if (json.contains(field)) {
                String[] parts = json.split("\"" + field + "\":\"");
                if (parts.length > 1) {
                    return parts[1].split("\"")[0];
                }
            }
            return "(파싱 실패)";
        } catch (Exception e) {
            return "(파싱 실패)";
        }
    }
}
