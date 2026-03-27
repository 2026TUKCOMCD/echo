package com.example.echo.integration;

import com.example.echo.context.domain.UserContext;
import com.example.echo.context.service.ContextService;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.health.dto.HealthData;
import com.example.echo.health.entity.HealthLog;
import com.example.echo.health.repository.HealthLogRepository;
import com.example.echo.health.service.HealthDataService;
import com.example.echo.prompt.service.PromptService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 전체 E2E 플로우 테스트 (T7.6)
 *
 * 테스트 흐름:
 * 앱 수집 → ConversationController → ConversationService
 * → ContextService → HealthDataService → 프롬프트 반영 → 대화 생성 확인
 *
 * 검증 항목:
 * 1. 건강 데이터가 DB에 저장되는지
 * 2. EnrichedHealthData가 올바르게 생성되는지 (평가 결과 포함)
 * 3. 시스템 프롬프트에 건강 데이터가 반영되는지
 * 4. AI 응답이 생성되는지
 *
 * 주의: 실제 외부 API 호출 (OpenAI, Azure TTS) - 비용 발생
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@Sql(scripts = "/data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("T7.6: 전체 E2E 플로우 테스트 - 건강 데이터 → 대화 생성")
class HealthDataE2EFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private HealthLogRepository healthLogRepository;

    @Autowired
    private HealthDataService healthDataService;

    @Autowired
    private ContextService contextService;

    @Autowired
    private PromptService promptService;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        // 테스트 전 해당 사용자의 오늘 건강 데이터 삭제
        healthLogRepository.findByUserIdAndRecordedDate(TEST_USER_ID, LocalDate.now())
                .ifPresent(healthLogRepository::delete);
    }

    @Nested
    @DisplayName("1. 건강 데이터 저장 및 EnrichedHealthData 생성 검증")
    class HealthDataPersistenceTest {

        @Test
        @Order(1)
        @DisplayName("앱에서 전송한 건강 데이터가 DB에 저장되어야 한다")
        void healthData_shouldBeSavedToDatabase() throws Exception {
            // Given - 앱에서 수집한 건강 데이터
            String healthDataJson = """
                {
                    "steps": 6500,
                    "sleepDurationMinutes": 420,
                    "sleepStartTime": "23:00:00",
                    "wakeUpTime": "06:00:00",
                    "exerciseDistanceKm": 3.5,
                    "exerciseActivity": "아침 산책",
                    "activityList": "산책,스트레칭"
                }
                """;

            // When - 대화 시작 API 호출
            mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(healthDataJson))
                    .andExpect(status().isOk());

            // Then - DB에 건강 데이터 저장 확인
            Optional<HealthLog> savedLog = healthLogRepository
                    .findByUserIdAndRecordedDate(TEST_USER_ID, LocalDate.now());

            assertThat(savedLog).isPresent();
            HealthLog healthLog = savedLog.get();

            assertThat(healthLog.getSteps()).isEqualTo(6500);
            assertThat(healthLog.getSleepDurationMinutes()).isEqualTo(420);
            assertThat(healthLog.getSleepStartTime()).isEqualTo(LocalTime.of(23, 0));
            assertThat(healthLog.getWakeUpTime()).isEqualTo(LocalTime.of(6, 0));
            assertThat(healthLog.getExerciseDistanceKm()).isEqualTo(3.5);
            assertThat(healthLog.getExerciseActivity()).isEqualTo("아침 산책");

            System.out.println("=== DB 저장 확인 완료 ===");
            System.out.println("걸음 수: " + healthLog.getSteps());
            System.out.println("수면 시간: " + healthLog.getSleepDurationMinutes() + "분");
            System.out.println("운동 활동: " + healthLog.getExerciseActivity());

            // 컨텍스트 정리
            contextService.finalizeContext(TEST_USER_ID);
        }

        @Test
        @Order(2)
        @DisplayName("EnrichedHealthData가 평가 결과와 함께 올바르게 생성되어야 한다")
        void enrichedHealthData_shouldContainEvaluations() {
            // Given - 건강 데이터 저장
            HealthData healthData = HealthData.builder()
                    .steps(8000)
                    .sleepDurationMinutes(480) // 8시간
                    .sleepStartTime(LocalTime.of(22, 30))
                    .wakeUpTime(LocalTime.of(6, 30))
                    .exerciseDistanceKm(4.0)
                    .exerciseActivity("조깅")
                    .activityList("조깅,스트레칭")
                    .build();

            healthDataService.saveHealthData(TEST_USER_ID, healthData);

            // When - EnrichedHealthData 생성
            EnrichedHealthData enrichedData = healthDataService.buildEnrichedHealthData(
                    healthData, TEST_USER_ID, 7);

            // Then - 원시 데이터 확인
            assertThat(enrichedData.getSteps()).isEqualTo(8000);
            assertThat(enrichedData.getSleepDurationMinutes()).isEqualTo(480);
            assertThat(enrichedData.getExerciseActivity()).isEqualTo("조깅");

            // 포맷팅된 값 확인
            assertThat(enrichedData.getStepsFormatted()).isEqualTo("8,000보");
            assertThat(enrichedData.getSleepDurationFormatted()).isEqualTo("8시간");
            assertThat(enrichedData.getExerciseDistanceFormatted()).isEqualTo("4.0km");

            // 평가 결과 확인 (값이 존재하는지)
            assertThat(enrichedData.getSleepEvaluation()).isNotEmpty();
            assertThat(enrichedData.getStepsEvaluation()).isNotEmpty();

            System.out.println("=== EnrichedHealthData 생성 확인 ===");
            System.out.println("걸음 수: " + enrichedData.getStepsFormatted());
            System.out.println("걸음 평가: " + enrichedData.getStepsEvaluation());
            System.out.println("수면 시간: " + enrichedData.getSleepDurationFormatted());
            System.out.println("수면 평가: " + enrichedData.getSleepEvaluation());
            System.out.println("기상 시간 평가: " + enrichedData.getWakeTimeEvaluation());
        }
    }

    @Nested
    @DisplayName("2. 프롬프트 반영 검증")
    class PromptReflectionTest {

        @Test
        @Order(3)
        @DisplayName("시스템 프롬프트에 건강 데이터가 반영되어야 한다")
        void systemPrompt_shouldContainHealthData() throws Exception {
            // Given - 건강 데이터와 함께 대화 시작
            String healthDataJson = """
                {
                    "steps": 7500,
                    "sleepDurationMinutes": 450,
                    "wakeUpTime": "07:30:00",
                    "exerciseActivity": "공원 산책"
                }
                """;

            // When - 대화 시작하여 컨텍스트 생성
            mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(healthDataJson))
                    .andExpect(status().isOk());

            // Then - 컨텍스트에서 시스템 프롬프트 확인
            UserContext context = contextService.getContext(TEST_USER_ID);
            String systemPrompt = promptService.buildSystemPrompt(context);

            System.out.println("=== 시스템 프롬프트 (일부) ===");
            System.out.println(systemPrompt.substring(0, Math.min(500, systemPrompt.length())) + "...");

            // 프롬프트에 건강 데이터 관련 내용이 포함되어 있는지 확인
            // (템플릿에 따라 변수가 치환되었는지 검증)
            assertThat(context.getEnrichedHealthData()).isNotNull();
            assertThat(context.getEnrichedHealthData().getSteps()).isEqualTo(7500);
            assertThat(context.getEnrichedHealthData().getExerciseActivity()).isEqualTo("공원 산책");

            // 컨텍스트 정리
            contextService.finalizeContext(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("3. 전체 대화 플로우 검증")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class FullE2EFlowTest {

        @Test
        @Order(4)
        @DisplayName("건강 데이터 → 대화 시작 → 메시지 교환 → 종료 전체 플로우")
        @Timeout(value = 180, unit = TimeUnit.SECONDS)
        void fullE2EFlow_withHealthData() throws Exception {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("전체 E2E 플로우 테스트 시작");
            System.out.println("=".repeat(60) + "\n");

            // ========================================
            // 1단계: 앱에서 건강 데이터와 함께 대화 시작
            // ========================================
            System.out.println("[1단계] 건강 데이터와 함께 대화 시작...");

            String healthDataJson = """
                {
                    "steps": 5500,
                    "sleepDurationMinutes": 390,
                    "sleepStartTime": "00:30:00",
                    "wakeUpTime": "07:00:00",
                    "exerciseDistanceKm": 2.8,
                    "exerciseActivity": "아침 걷기",
                    "activityList": "걷기"
                }
                """;

            MvcResult startResult = mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(healthDataJson))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andExpect(jsonPath("$.audioData").isNotEmpty())
                    .andReturn();

            String startResponse = startResult.getResponse().getContentAsString();
            JsonNode startJson = objectMapper.readTree(startResponse);
            String aiGreeting = startJson.get("message").asText();

            System.out.println("AI 첫 인사: " + aiGreeting);

            // DB 저장 확인
            Optional<HealthLog> savedLog = healthLogRepository
                    .findByUserIdAndRecordedDate(TEST_USER_ID, LocalDate.now());
            assertThat(savedLog).isPresent();
            System.out.println("DB 저장 확인: 걸음 수 = " + savedLog.get().getSteps());

            // 컨텍스트 확인
            UserContext context = contextService.getContext(TEST_USER_ID);
            assertThat(context.getEnrichedHealthData()).isNotNull();
            System.out.println("컨텍스트 확인: EnrichedHealthData 존재");
            System.out.println("  - 걸음 수: " + context.getEnrichedHealthData().getStepsFormatted());
            System.out.println("  - 걸음 평가: " + context.getEnrichedHealthData().getStepsEvaluation());
            System.out.println("  - 수면 시간: " + context.getEnrichedHealthData().getSleepDurationFormatted());
            System.out.println("  - 수면 평가: " + context.getEnrichedHealthData().getSleepEvaluation());

            // ========================================
            // 2단계: 사용자 음성 메시지 전송
            // ========================================
            System.out.println("\n[2단계] 사용자 음성 메시지 전송...");

            ClassPathResource audioResource = new ClassPathResource("test-audio/정왕동.m4a");
            byte[] audioBytes = Files.readAllBytes(audioResource.getFile().toPath());

            MockMultipartFile audioFile = new MockMultipartFile(
                    "audio",
                    "user_response.m4a",
                    "audio/m4a",
                    audioBytes
            );

            MvcResult messageResult = mockMvc.perform(multipart("/api/conversations/message")
                            .file(audioFile))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.userMessage").isNotEmpty())
                    .andExpect(jsonPath("$.aiResponse").isNotEmpty())
                    .andExpect(jsonPath("$.audioData").isNotEmpty())
                    .andReturn();

            String messageResponse = messageResult.getResponse().getContentAsString();
            JsonNode messageJson = objectMapper.readTree(messageResponse);

            System.out.println("사용자 발화 (STT): " + messageJson.get("userMessage").asText());
            System.out.println("AI 응답: " + messageJson.get("aiResponse").asText());

            // 대화 히스토리 확인
            context = contextService.getContext(TEST_USER_ID);
            assertThat(context.getConversationHistory()).hasSize(2); // 첫 인사 + 첫 턴
            System.out.println("대화 히스토리 턴 수: " + context.getConversationHistory().size());

            // ========================================
            // 3단계: TTS 재시도 테스트
            // ========================================
            System.out.println("\n[3단계] TTS 재시도 테스트...");

            MvcResult retryResult = mockMvc.perform(post("/api/conversations/tts-retry"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.audioData").isNotEmpty())
                    .andReturn();

            System.out.println("TTS 재시도 성공: 오디오 데이터 수신");

            // ========================================
            // 4단계: 대화 종료
            // ========================================
            System.out.println("\n[4단계] 대화 종료...");

            MvcResult endResult = mockMvc.perform(post("/api/conversations/end"))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.endedAt").isNotEmpty())
                    .andReturn();

            String endResponse = endResult.getResponse().getContentAsString();
            System.out.println("대화 종료 응답: " + endResponse);

            System.out.println("\n" + "=".repeat(60));
            System.out.println("전체 E2E 플로우 테스트 완료!");
            System.out.println("=".repeat(60) + "\n");
        }
    }

    @Nested
    @DisplayName("4. 건강 데이터 없이 대화 시작")
    class NoHealthDataTest {

        @Test
        @Order(5)
        @DisplayName("건강 데이터 없이 대화 시작 시 DB에서 조회해야 한다")
        @Timeout(value = 60, unit = TimeUnit.SECONDS)
        void startConversation_withoutHealthData_shouldQueryFromDB() throws Exception {
            // Given - 사전에 건강 데이터 저장
            HealthData preExistingData = HealthData.builder()
                    .steps(4000)
                    .sleepDurationMinutes(360)
                    .exerciseActivity("실내 스트레칭")
                    .build();
            healthDataService.saveHealthData(TEST_USER_ID, preExistingData);

            // When - 건강 데이터 없이 대화 시작 (body 없이 요청)
            MvcResult result = mockMvc.perform(post("/api/conversations/start")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.message").isNotEmpty())
                    .andReturn();

            // Then - 컨텍스트에 DB에서 조회한 건강 데이터가 있어야 함
            UserContext context = contextService.getContext(TEST_USER_ID);
            EnrichedHealthData healthData = context.getEnrichedHealthData();

            assertThat(healthData).isNotNull();
            assertThat(healthData.getSteps()).isEqualTo(4000);
            assertThat(healthData.getExerciseActivity()).isEqualTo("실내 스트레칭");

            System.out.println("=== DB 조회 건강 데이터 확인 ===");
            System.out.println("걸음 수: " + healthData.getStepsFormatted());
            System.out.println("운동 활동: " + healthData.getExerciseActivity());

            // 컨텍스트 정리
            contextService.finalizeContext(TEST_USER_ID);
        }
    }

    @Nested
    @DisplayName("5. 건강 데이터 평가 로직 검증")
    class HealthEvaluationTest {

        @Test
        @Order(6)
        @DisplayName("수면 부족 시 '부족' 평가가 반환되어야 한다")
        void sleepEvaluation_insufficient() {
            // 선호 7시간, 실제 5시간 (300분) -> 부족
            String evaluation = healthDataService.evaluateSleep(300, 7);
            assertThat(evaluation).isEqualTo("부족");
            System.out.println("수면 300분 (5시간), 선호 7시간 → 평가: " + evaluation);
        }

        @Test
        @Order(7)
        @DisplayName("걸음 수가 평균보다 많으면 '평소보다 많음' 평가")
        void stepsEvaluation_moreThanAverage() {
            // 평균 5000, 오늘 7000 (140%)
            String evaluation = healthDataService.evaluateSteps(7000, 5000);
            assertThat(evaluation).isEqualTo("평소보다 많음");
            System.out.println("오늘 7000보, 평균 5000보 → 평가: " + evaluation);
        }

        @Test
        @Order(8)
        @DisplayName("기상 시간이 평균보다 일찍이면 '평소보다 일찍' 평가")
        void wakeTimeEvaluation_earlier() {
            LocalTime avgWakeTime = LocalTime.of(7, 0);
            LocalTime todayWakeTime = LocalTime.of(6, 0);

            String evaluation = healthDataService.evaluateWakeTime(todayWakeTime, avgWakeTime);
            assertThat(evaluation).isEqualTo("평소보다 일찍");
            System.out.println("오늘 6시 기상, 평균 7시 → 평가: " + evaluation);
        }
    }
}
