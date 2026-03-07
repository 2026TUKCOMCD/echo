package com.example.echo.prompt.service;

import com.example.echo.common.dto.WeatherData;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.prompt.entity.PromptTemplate;
import com.example.echo.prompt.entity.PromptType;
import com.example.echo.prompt.repository.PromptTemplateRepository;
import com.example.echo.user.dto.UserPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)//Mock 설정(@Mock 필드 Mock 객체 자동 생성 등..)
class PromptServiceTest {

    @Mock
    private PromptTemplateRepository promptTemplateRepository;

    @InjectMocks
    private PromptService promptService;

    private UserContext context;
    private EnrichedHealthData enrichedHealthData;
    private final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        UserPreferences preferences = UserPreferences.builder()
                .userId(TEST_USER_ID)
                .name("홍길동")
                .age(65)
                .location("서울")
                .preferredSleepHours(7)
                .build();

        WeatherData weatherData = WeatherData.builder()
                .description("맑음")
                .temperature(20)
                .build();

        // EnrichedHealthData 설정 (테스트용)
        enrichedHealthData = EnrichedHealthData.builder()
                .steps(5000)
                .sleepDurationMinutes(420)
                .stepsFormatted("5,000보")
                .sleepDurationFormatted("7시간")
                .sleepStartTimeFormatted("")
                .wakeUpTimeFormatted("")
                .exerciseDistanceFormatted("")
                .stepsEvaluation("평소와 비슷")
                .sleepEvaluation("적당")
                .wakeTimeEvaluation("")
                .build();

        // UserContext에 EnrichedHealthData 직접 설정 (DB 접근 없음)
        context = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(preferences)
                .enrichedHealthData(enrichedHealthData)
                .todayWeather(weatherData)
                .build();
    }

    // ===== buildSystemPrompt 테스트 =====

    @Test
    @DisplayName("buildSystemPrompt - 정상 케이스: 템플릿 변수가 실제 값으로 치환됨")
    void buildSystemPrompt_success() {
        // Given
        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{userName}}님은 {{userAge}}세입니다.")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When (EnrichedHealthData는 이미 Context에 있으므로 DB 조회 없음)
        String result = promptService.buildSystemPrompt(context);

        // Then
        assertThat(result).isEqualTo("홍길동님은 65세입니다.");
    }

    @Test
    @DisplayName("buildSystemPrompt - 템플릿 없음: IllegalStateException 발생")
    void buildSystemPrompt_templateNotFound() {
        // Given
        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> promptService.buildSystemPrompt(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("활성화된 SYSTEM 프롬프트 템플릿이 없습니다.");
    }

    @Test
    @DisplayName("buildSystemPrompt - preferences가 null: 기본값 '사용자'로 치환")
    void buildSystemPrompt_nullPreferences() {
        // Given
        UserContext contextWithoutPreferences = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(null)
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("안녕하세요 {{userName}}님")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(contextWithoutPreferences);

        // Then
        assertThat(result).isEqualTo("안녕하세요 사용자님");
    }

    // ===== buildConversationPrompt 테스트 =====

    @Test
    @DisplayName("buildConversationPrompt - 정상 케이스: 3개 변수가 모두 치환됨")
    void buildConversationPrompt_success() {
        // Given
        PromptTemplate conversationTemplate = PromptTemplate.builder()
                .type(PromptType.CONVERSATION)
                .content("[시스템]{{systemPrompt}}\n[히스토리]{{conversationHistory}}\n[메시지]{{userMessage}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.CONVERSATION))
                .thenReturn(Optional.of(conversationTemplate));

        // Context에 시스템 프롬프트 캐싱 (실제 흐름과 동일)
        context.setSystemPrompt("시스템: 홍길동");

        // When
        String result = promptService.buildConversationPrompt(context, "오늘 날씨 어때요?");

        // Then
        assertThat(result).contains("시스템: 홍길동");
        assertThat(result).contains("오늘 날씨 어때요?");
    }

    // ===== buildHistory 테스트 =====

    @Test
    @DisplayName("buildHistory - 대화 히스토리 존재: 턴별로 포맷팅")
    void buildHistory_withHistory() {
        // Given
        List<ConversationTurn> history = new ArrayList<>();
        history.add(ConversationTurn.builder()
                .userMessage("안녕하세요")
                .aiResponse("안녕하세요! 오늘 기분이 어떠세요?")
                .timestamp(LocalDateTime.now())
                .build());
        history.add(ConversationTurn.builder()
                .userMessage("좋아요")
                .aiResponse("다행이네요!")
                .timestamp(LocalDateTime.now())
                .build());

        UserContext contextWithHistory = UserContext.builder()
                .userId(TEST_USER_ID)
                .conversationHistory(history)
                .build();

        // When
        String result = promptService.buildHistory(contextWithHistory);

        // Then
        assertThat(result).contains("[턴 1]");
        assertThat(result).contains("사용자: 안녕하세요");
        assertThat(result).contains("AI: 안녕하세요! 오늘 기분이 어떠세요?");
        assertThat(result).contains("[턴 2]");
        assertThat(result).contains("사용자: 좋아요");
        assertThat(result).contains("AI: 다행이네요!");
    }

    @Test
    @DisplayName("buildHistory - 히스토리 비어있음: 빈 문자열 반환")
    void buildHistory_emptyHistory() {
        // Given
        UserContext emptyHistoryContext = UserContext.builder()
                .userId(TEST_USER_ID)
                .conversationHistory(new ArrayList<>())
                .build();

        // When
        String result = promptService.buildHistory(emptyHistoryContext);

        // Then
        assertThat(result).isEmpty();
    }

    // ===== buildSystemPrompt 건강 데이터 테스트 =====

    @Test
    @DisplayName("buildSystemPrompt - 건강 데이터 변수 치환 확인")
    void buildSystemPrompt_healthDataVariables() {
        // Given
        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("걸음: {{steps}}, 수면: {{sleepInfo}}, 걸음평가: {{stepsEvaluation}}, 수면평가: {{sleepEvaluation}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(context);

        // Then
        assertThat(result).contains("걸음: 5,000보");
        assertThat(result).contains("수면: 7시간");
        assertThat(result).contains("걸음평가: 평소와 비슷");
        assertThat(result).contains("수면평가: 적당");
    }

    @Test
    @DisplayName("buildSystemPrompt - 건강 데이터 null: 빈 문자열로 치환")
    void buildSystemPrompt_nullHealthData() {
        // Given
        UserContext noHealthContext = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(context.getPreferences())
                .enrichedHealthData(null)
                .todayWeather(null)
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("걸음: [{{steps}}], 수면평가: [{{sleepEvaluation}}]")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(noHealthContext);

        // Then
        assertThat(result).isEqualTo("걸음: [], 수면평가: []");
    }

    @Test
    @DisplayName("buildHistory - null 히스토리: 빈 문자열 반환")
    void buildHistory_nullHistory() {
        // Given
        UserContext nullHistoryContext = UserContext.builder()
                .userId(TEST_USER_ID)
                .conversationHistory(null)
                .build();

        // When
        String result = promptService.buildHistory(nullHistoryContext);

        // Then
        assertThat(result).isEmpty();
    }
}
