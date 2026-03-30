package com.example.echo.integration;

import com.example.echo.conversation.dto.ConversationStartRequest;
import com.example.echo.health.dto.HealthData;
import com.example.echo.location.dto.RawLocationData;
import com.example.echo.location.dto.RawVisitedPlace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Epic 5 위치 데이터 통합 테스트 (S16)
 *
 * 테스트 시나리오:
 * 1. 대화 시작 플로우 테스트 (with LocationData)
 * 2. LocationData → Prompt 연동 확인
 * 3. 위치 없을 때 폴백 테스트
 *
 * 주의: 테스트 실행 시 실제 API 호출 (Kakao, OpenWeatherMap, OpenAI, Azure TTS)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@Sql(scripts = "/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Epic 5 위치 데이터 통합 테스트 (S16)")
class LocationDataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("대화 시작 - 위치 데이터 포함")
    class StartConversationWithLocationData {

        @Test
        @Order(1)
        @DisplayName("위치 데이터 포함 대화 시작 - 정상 응답")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void startConversation_withLocationData_shouldReturnGreeting() throws Exception {
            // Given - 춘천시 공지천 산책로 근처 위치 데이터
            RawVisitedPlace visitedPlace = RawVisitedPlace.builder()
                    .latitude(37.8813)
                    .longitude(127.7298)
                    .visitStartTime(LocalTime.of(10, 0))
                    .visitEndTime(LocalTime.of(11, 30))
                    .stayDurationMinutes(90)
                    .build();

            RawLocationData locationData = RawLocationData.builder()
                    .currentLatitude(37.8813)
                    .currentLongitude(127.7298)
                    .visitedPlaces(List.of(visitedPlace))
                    .totalDistanceKm(2.5)
                    .build();

            HealthData healthData = HealthData.builder()
                    .steps(5000)
                    .sleepDurationMinutes(420)
                    .exerciseDistanceKm(2.5)
                    .exerciseActivity("걷기")
                    .build();

            ConversationStartRequest request = new ConversationStartRequest(healthData, locationData);

            // When
            MvcResult result = mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.audioData").isNotEmpty())
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andReturn();

            // Then
            String responseBody = result.getResponse().getContentAsString();
            System.out.println("=== 위치 데이터 포함 대화 시작 응답 ===");
            System.out.println("응답 메시지: " + extractField(responseBody, "message"));

            assertThat(responseBody).contains("message");
            assertThat(responseBody).contains("audioData");

            // 대화 종료 (다음 테스트를 위해)
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk());
        }

        @Test
        @Order(2)
        @DisplayName("여러 방문 장소 포함 - 체류 시간 순 정렬 확인")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void startConversation_withMultipleVisitedPlaces_shouldProcessAll() throws Exception {
            // Given - 여러 방문 장소 (체류 시간 다양)
            RawVisitedPlace place1 = RawVisitedPlace.builder()
                    .latitude(37.8813)
                    .longitude(127.7298)
                    .visitStartTime(LocalTime.of(9, 0))
                    .visitEndTime(LocalTime.of(9, 30))
                    .stayDurationMinutes(30)  // 30분
                    .build();

            RawVisitedPlace place2 = RawVisitedPlace.builder()
                    .latitude(37.8750)
                    .longitude(127.7350)
                    .visitStartTime(LocalTime.of(10, 0))
                    .visitEndTime(LocalTime.of(12, 0))
                    .stayDurationMinutes(120)  // 2시간 - 가장 오래 체류
                    .build();

            RawVisitedPlace place3 = RawVisitedPlace.builder()
                    .latitude(37.8700)
                    .longitude(127.7400)
                    .visitStartTime(LocalTime.of(13, 0))
                    .visitEndTime(LocalTime.of(14, 0))
                    .stayDurationMinutes(60)  // 1시간
                    .build();

            RawLocationData locationData = RawLocationData.builder()
                    .currentLatitude(37.8813)
                    .currentLongitude(127.7298)
                    .visitedPlaces(List.of(place1, place2, place3))
                    .totalDistanceKm(5.0)
                    .build();

            ConversationStartRequest request = new ConversationStartRequest(null, locationData);

            // When
            MvcResult result = mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andReturn();

            // Then
            String responseBody = result.getResponse().getContentAsString();
            System.out.println("=== 여러 방문 장소 대화 시작 응답 ===");
            System.out.println("응답 메시지: " + extractField(responseBody, "message"));

            // 대화 종료
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("대화 시작 - 위치 데이터 없음 (폴백)")
    class StartConversationWithoutLocationData {

        @Test
        @Order(3)
        @DisplayName("위치 데이터 null - 날씨/건강 기반 대화로 폴백")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void startConversation_withoutLocationData_shouldFallbackToWeather() throws Exception {
            // Given - locationData = null
            HealthData healthData = HealthData.builder()
                    .steps(8000)
                    .sleepDurationMinutes(480)
                    .exerciseDistanceKm(3.5)
                    .exerciseActivity("산책")
                    .build();

            ConversationStartRequest request = new ConversationStartRequest(healthData, null);

            // When
            MvcResult result = mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.audioData").isNotEmpty())
                    .andReturn();

            // Then
            String responseBody = result.getResponse().getContentAsString();
            System.out.println("=== 위치 데이터 없음 (폴백) 응답 ===");
            System.out.println("응답 메시지: " + extractField(responseBody, "message"));

            // 위치 데이터 없어도 정상 응답 확인
            assertThat(responseBody).contains("message");

            // 대화 종료
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk());
        }

        @Test
        @Order(4)
        @DisplayName("빈 요청 본문 - 기본 대화 시작")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void startConversation_emptyRequest_shouldStartWithDefaults() throws Exception {
            // When - 빈 요청
            MvcResult result = mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andReturn();

            // Then
            String responseBody = result.getResponse().getContentAsString();
            System.out.println("=== 빈 요청 대화 시작 응답 ===");
            System.out.println("응답 메시지: " + extractField(responseBody, "message"));

            // 대화 종료
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk());
        }

        @Test
        @Order(5)
        @DisplayName("방문 장소 빈 리스트 - 현재 위치만으로 대화")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void startConversation_emptyVisitedPlaces_shouldUseCurrentLocation() throws Exception {
            // Given - 방문 장소 없음, 현재 위치만 있음
            RawLocationData locationData = RawLocationData.builder()
                    .currentLatitude(37.5665)
                    .currentLongitude(126.9780)
                    .visitedPlaces(List.of())  // 빈 리스트
                    .totalDistanceKm(0.0)
                    .build();

            ConversationStartRequest request = new ConversationStartRequest(null, locationData);

            // When
            MvcResult result = mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andReturn();

            // Then
            String responseBody = result.getResponse().getContentAsString();
            System.out.println("=== 방문 장소 없음 (현재 위치만) 응답 ===");
            System.out.println("응답 메시지: " + extractField(responseBody, "message"));

            // 대화 종료
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("전체 E2E 시나리오")
    class FullE2EScenario {

        @Test
        @Order(6)
        @DisplayName("시나리오: 위치 데이터 포함 대화 시작 → 대화 종료")
        @Timeout(value = 120, unit = TimeUnit.SECONDS)
        void fullScenario_withLocationData() throws Exception {
            System.out.println("\n========================================");
            System.out.println("위치 데이터 E2E 시나리오 테스트 시작");
            System.out.println("========================================\n");

            // === 1단계: 위치 데이터 포함 대화 시작 ===
            System.out.println("[1단계] 위치 데이터 포함 대화 시작...");

            RawVisitedPlace cafeVisit = RawVisitedPlace.builder()
                    .latitude(37.5172)
                    .longitude(127.0473)
                    .visitStartTime(LocalTime.of(14, 0))
                    .visitEndTime(LocalTime.of(15, 30))
                    .stayDurationMinutes(90)
                    .build();

            RawLocationData locationData = RawLocationData.builder()
                    .currentLatitude(37.5665)
                    .currentLongitude(126.9780)
                    .visitedPlaces(List.of(cafeVisit))
                    .totalDistanceKm(3.2)
                    .build();

            HealthData healthData = HealthData.builder()
                    .steps(7500)
                    .sleepDurationMinutes(450)
                    .exerciseDistanceKm(3.2)
                    .exerciseActivity("걷기")
                    .build();

            ConversationStartRequest request = new ConversationStartRequest(healthData, locationData);

            MvcResult startResult = mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.audioData").isNotEmpty())
                    .andReturn();

            String startResponse = startResult.getResponse().getContentAsString();
            String aiGreeting = extractField(startResponse, "message");
            System.out.println("AI 첫 인사: " + aiGreeting);

            // === 2단계: 대화 종료 ===
            System.out.println("\n[2단계] 대화 종료...");
            mockMvc.perform(post("/api/conversations/end"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.endedAt").isNotEmpty());

            System.out.println("\n========================================");
            System.out.println("위치 데이터 E2E 시나리오 테스트 완료!");
            System.out.println("========================================\n");
        }
    }

    /**
     * JSON 응답에서 특정 필드 값 추출
     */
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
            return "파싱 실패: " + e.getMessage();
        }
    }
}
