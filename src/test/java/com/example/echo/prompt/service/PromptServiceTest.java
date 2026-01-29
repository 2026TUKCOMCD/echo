package com.example.echo.prompt.service;

import com.example.echo.common.dto.WeatherData;
import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.dto.HealthData;
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
    private final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        UserPreferences preferences = UserPreferences.builder()
                .userId(TEST_USER_ID)
                .name("홍길동")
                .age(65)
                .location("서울")
                .build();

        HealthData healthData = HealthData.builder()
                .steps(5000)
                .sleepDurationMinutes(420)
                .build();

        WeatherData weatherData = WeatherData.builder()
                .description("맑음")
                .temperature(20)
                .build();

        context = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(preferences)
                .todayHealthData(healthData)
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

        when(promptTemplateRepository.findByTypeAndIsActiveTrue(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(context);

        // Then
        assertThat(result).isEqualTo("홍길동님은 65세입니다.");
    }

    @Test
    @DisplayName("buildSystemPrompt - 템플릿 없음: IllegalStateException 발생")
    void buildSystemPrompt_templateNotFound() {
        // Given
        when(promptTemplateRepository.findByTypeAndIsActiveTrue(PromptType.SYSTEM))
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

        when(promptTemplateRepository.findByTypeAndIsActiveTrue(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(contextWithoutPreferences);

        // Then
        assertThat(result).isEqualTo("안녕하세요 사용자님");
    }

    // ===== buildConversationPrompt 테스트 =====

    @Test
    @DisplayName("buildConversationPrompt - 정상 케이스: 4개 변수가 모두 치환됨")
    void buildConversationPrompt_success() {
        // Given
        PromptTemplate systemTemplate = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("시스템: {{userName}}")
                .build();

        PromptTemplate conversationTemplate = PromptTemplate.builder()
                .type(PromptType.CONVERSATION)
                .content("[시스템]{{systemPrompt}}\n[컨텍스트]{{todayContext}}\n[히스토리]{{conversationHistory}}\n[메시지]{{userMessage}}")
                .build();

        when(promptTemplateRepository.findByTypeAndIsActiveTrue(PromptType.SYSTEM))
                .thenReturn(Optional.of(systemTemplate));
        when(promptTemplateRepository.findByTypeAndIsActiveTrue(PromptType.CONVERSATION))
                .thenReturn(Optional.of(conversationTemplate));

        // When
        String result = promptService.buildConversationPrompt(context, "오늘 날씨 어때요?");

        // Then
        assertThat(result).contains("시스템: 홍길동");
        assertThat(result).contains("5,000보 걸으셨고");
        assertThat(result).contains("오늘 날씨 어때요?");
    }

    // ===== buildTodayContext 테스트 =====

    @Test
    @DisplayName("buildTodayContext - 건강+날씨 모두 있음: 건강 정보와 날씨 정보가 함께 포맷팅")
    void buildTodayContext_healthAndWeather() {
        // When
        String result = promptService.buildTodayContext(context);

        // Then
        assertThat(result).contains("홍길동님은");
        assertThat(result).contains("5,000보 걸으셨고");
        assertThat(result).contains("7.0시간 주무셨습니다.");
        assertThat(result).contains("오늘 날씨는 맑음이고 기온은 20도입니다.");
    }

    @Test
    @DisplayName("buildTodayContext - 건강 데이터만 있음: 날씨 없이 건강 정보만 출력")
    void buildTodayContext_healthOnly() {
        // Given
        UserContext healthOnlyContext = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(context.getPreferences())
                .todayHealthData(context.getTodayHealthData())
                .todayWeather(null)
                .build();

        // When
        String result = promptService.buildTodayContext(healthOnlyContext);

        // Then
        assertThat(result).contains("5,000보 걸으셨고");
        assertThat(result).contains("7.0시간 주무셨습니다.");
        assertThat(result).doesNotContain("날씨");
    }

    @Test
    @DisplayName("buildTodayContext - 날씨만 있음: 건강 없이 날씨 정보만 출력")
    void buildTodayContext_weatherOnly() {
        // Given
        UserContext weatherOnlyContext = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(context.getPreferences())
                .todayHealthData(null)
                .todayWeather(context.getTodayWeather())
                .build();

        // When
        String result = promptService.buildTodayContext(weatherOnlyContext);

        // Then
        assertThat(result).doesNotContain("걸으셨고");
        assertThat(result).doesNotContain("주무셨습니다");
        assertThat(result).contains("오늘 날씨는 맑음이고 기온은 20도입니다.");
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
}
