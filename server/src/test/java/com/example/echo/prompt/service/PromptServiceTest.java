package com.example.echo.prompt.service;

import com.example.echo.common.dto.WeatherData;
import com.example.echo.context.domain.UserContext;
import com.example.echo.health.dto.EnrichedHealthData;
import com.example.echo.prompt.entity.PromptTemplate;
import com.example.echo.prompt.entity.PromptType;
import com.example.echo.prompt.repository.PromptTemplateRepository;
import com.example.echo.location.dto.LocationData;
import com.example.echo.location.dto.VisitedPlace;
import com.example.echo.user.dto.UserPreferences;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.echo.memory.entity.Memory;

import java.time.LocalTime;
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
    @DisplayName("buildSystemPrompt - 장기기억이 {{lifeMemories}} 자리에 치환됨")
    void buildSystemPrompt_lifeMemoriesSubstituted() {
        // Given
        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("[지난 이야기]\n{{lifeMemories}}")
                .build();
        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        List<Memory> memories = List.of(
                Memory.builder().userId(TEST_USER_ID).lifePeriod("청년기").topic("직업")
                        .content("30대에 부산에서 어부로 일했다").tags("부산,어부").build(),
                Memory.builder().userId(TEST_USER_ID).lifePeriod("중년기").topic("가족")
                        .content("손주 이름은 민준이다").build());

        // When
        String result = promptService.buildSystemPrompt(context, memories);

        // Then
        assertThat(result).contains("- [청년기/직업] 30대에 부산에서 어부로 일했다 (태그: 부산,어부)");
        // 태그가 없으면 태그 표기를 붙이지 않음
        assertThat(result).contains("- [중년기/가족] 손주 이름은 민준이다");
        assertThat(result).doesNotContain("태그: null");
        assertThat(result).doesNotContain("{{lifeMemories}}");
    }

    @Test
    @DisplayName("buildSystemPrompt - 저장된 장기기억이 없으면 없다고 명시해 AI가 아는 척하지 않게 함")
    void buildSystemPrompt_noLifeMemories() {
        // Given
        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{lifeMemories}}")
                .build();
        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When (기존 단일 인자 호출은 기억 없음과 동일하게 동작해야 함)
        String result = promptService.buildSystemPrompt(context);

        // Then
        assertThat(result).contains("아직 들려주신 옛 이야기가 없습니다");
        assertThat(result).doesNotContain("{{lifeMemories}}");
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

    // ===== buildSystemPrompt 건강 데이터 테스트 =====

    @Test
    @DisplayName("buildSystemPrompt - 건강 데이터 변수 치환 확인")
    void buildSystemPrompt_healthDataVariables() {
        // Given
        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("걸음: {{steps}}, 수면: {{sleepDuration}}, 걸음평가: {{stepsEvaluation}}, 수면평가: {{sleepEvaluation}}")
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

    // ===== buildSystemPrompt 위치 데이터 테스트 =====

    @Test
    @DisplayName("buildSystemPrompt - 방문 장소 체류 시간 내림차순 정렬 확인")
    void buildSystemPrompt_visitedPlacesSortedByStayDuration() {
        // Given: 체류 시간이 다른 3개 장소 (정렬되지 않은 순서로 입력)
        List<VisitedPlace> places = List.of(
                VisitedPlace.builder().placeName("편의점").stayDurationMinutes(10).build(),
                VisitedPlace.builder().placeName("이마트").stayDurationMinutes(45).build(),
                VisitedPlace.builder().placeName("공원").stayDurationMinutes(30).build()
        );

        LocationData locationData = LocationData.builder()
                .currentCity("서울")
                .visitedPlaces(places)
                .build();

        UserContext contextWithLocation = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(context.getPreferences())
                .enrichedHealthData(enrichedHealthData)
                .locationData(locationData)
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("방문장소: {{visitedPlacesText}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(contextWithLocation);

        // Then: 체류 시간 내림차순 정렬 (이마트 > 공원 > 편의점)
        int indexEmart = result.indexOf("이마트");
        int indexPark = result.indexOf("공원");
        int indexStore = result.indexOf("편의점");

        assertThat(indexEmart).isLessThan(indexPark);
        assertThat(indexPark).isLessThan(indexStore);
    }

    @Test
    @DisplayName("buildSystemPrompt - 방문 장소 체류 시간 미표시 확인")
    void buildSystemPrompt_visitedPlacesWithoutDuration() {
        // Given
        List<VisitedPlace> places = List.of(
                VisitedPlace.builder().placeName("스타벅스").stayDurationMinutes(60).build()
        );

        LocationData locationData = LocationData.builder()
                .currentCity("서울")
                .visitedPlaces(places)
                .build();

        UserContext contextWithLocation = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(context.getPreferences())
                .locationData(locationData)
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{visitedPlacesText}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(contextWithLocation);

        // Then: 체류 시간(분)이 표시되지 않음
        assertThat(result).contains("스타벅스");
        assertThat(result).doesNotContain("60");
        assertThat(result).doesNotContain("분");
        assertThat(result).doesNotContain("체류");
    }

    @Test
    @DisplayName("buildSystemPrompt - 역지오코딩 실패로 placeName이 null인 장소는 목록에서 제외됨 (회귀 방지)")
    void buildSystemPrompt_excludesPlacesWithNullPlaceName() {
        // Given: 역지오코딩 실패 장소(placeName null) + 성공 장소가 섞여 있음
        List<VisitedPlace> places = List.of(
                VisitedPlace.builder().placeName(null).stayDurationMinutes(50).build(),
                VisitedPlace.builder().placeName("").stayDurationMinutes(40).build(),
                VisitedPlace.builder().placeName("이마트").stayDurationMinutes(30).build()
        );

        LocationData locationData = LocationData.builder()
                .currentCity("서울")
                .visitedPlaces(places)
                .build();

        UserContext contextWithLocation = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(context.getPreferences())
                .locationData(locationData)
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{visitedPlacesText}} / {{todayActivityGuide}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(contextWithLocation);

        // Then: null 장소가 "- null"로 새지 않고, 유효한 장소만 남아 "장소 있음" 지시문으로 이어짐
        assertThat(result).doesNotContain("null");
        assertThat(result).contains("이마트");
        assertThat(result).contains("장소를 언급하며");
    }

    @Test
    @DisplayName("buildSystemPrompt - 위치 데이터가 아예 없으면 장소 언급 없이 활동만 묻는 지시문이 들어감")
    void buildSystemPrompt_todayActivityGuide_noLocationData() {
        // Given: locationData 자체가 null
        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{todayActivityGuide}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(context);

        // Then
        assertThat(result).contains("장소를 절대 언급하지 말고");
    }

    @Test
    @DisplayName("buildSystemPrompt - 방문 장소는 있지만 전부 역지오코딩 실패면 장소 언급 없이 활동만 묻는 지시문이 들어감")
    void buildSystemPrompt_todayActivityGuide_allPlacesUnnamed() {
        // Given: visitedPlaces는 존재하지만 전부 placeName이 없음
        List<VisitedPlace> places = List.of(
                VisitedPlace.builder().placeName(null).stayDurationMinutes(20).build()
        );
        LocationData locationData = LocationData.builder().currentCity("서울").visitedPlaces(places).build();

        UserContext contextWithLocation = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(context.getPreferences())
                .locationData(locationData)
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{todayActivityGuide}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(contextWithLocation);

        // Then
        assertThat(result).contains("장소를 절대 언급하지 말고");
    }

    @Test
    @DisplayName("buildSystemPrompt - 유효한 방문 장소가 있으면 장소를 언급하며 활동을 묻는 지시문이 들어감")
    void buildSystemPrompt_todayActivityGuide_hasNamedPlace() {
        // Given
        List<VisitedPlace> places = List.of(
                VisitedPlace.builder().placeName("신길로 123").stayDurationMinutes(90).build()
        );
        LocationData locationData = LocationData.builder().currentCity("서울").visitedPlaces(places).build();

        UserContext contextWithLocation = UserContext.builder()
                .userId(TEST_USER_ID)
                .preferences(context.getPreferences())
                .locationData(locationData)
                .build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{todayActivityGuide}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(contextWithLocation);

        // Then
        assertThat(result).contains("장소를 언급하며");
        assertThat(result).doesNotContain("장소를 절대 언급하지 말고");
    }

    // ===== 집/외출 분류(isHome) 반영 테스트 =====

    @Test
    @DisplayName("buildSystemPrompt - 외출 + 집 혼합: [외출한 곳]/[집에서 보낸 시간] 분리, 집 주소 미노출, 외출 우선 가이드")
    void buildSystemPrompt_outingAndHome_split() {
        // Given: 외출(이마트) + 낮 집 체류(자택)
        List<VisitedPlace> places = List.of(
                VisitedPlace.builder().placeName("이마트").isHome(false)
                        .visitStartTime(LocalTime.of(14, 0)).visitEndTime(LocalTime.of(15, 0))
                        .stayDurationMinutes(60).build(),
                VisitedPlace.builder().placeName("자택").isHome(true)
                        .visitStartTime(LocalTime.of(9, 0)).visitEndTime(LocalTime.of(13, 0))
                        .stayDurationMinutes(240).build()
        );
        LocationData locationData = LocationData.builder().currentCity("서울").visitedPlaces(places).build();
        UserContext ctx = UserContext.builder()
                .userId(TEST_USER_ID).preferences(context.getPreferences()).locationData(locationData).build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{visitedPlacesText}} / {{todayActivityGuide}}")
                .build();
        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(ctx);

        // Then: 두 섹션으로 분리되고, 집 장소명(자택)은 노출되지 않으며, 외출 우선 가이드가 들어간다
        assertThat(result).contains("[외출한 곳]");
        assertThat(result).contains("이마트");
        assertThat(result).contains("[집에서 보낸 시간]");
        assertThat(result).doesNotContain("자택");           // 집 주소는 노출하지 않음
        assertThat(result).contains("장소를 언급하며");        // 외출 우선 가이드
    }

    @Test
    @DisplayName("buildSystemPrompt - 외출 없이 낮 집 체류만: 집에서 어떻게 지냈는지 묻는 가이드")
    void buildSystemPrompt_daytimeHomeOnly() {
        // Given: 낮 집 체류만 (외출 없음)
        List<VisitedPlace> places = List.of(
                VisitedPlace.builder().placeName("자택").isHome(true)
                        .visitStartTime(LocalTime.of(10, 0)).visitEndTime(LocalTime.of(13, 0))
                        .stayDurationMinutes(180).build()
        );
        LocationData locationData = LocationData.builder().currentCity("서울").visitedPlaces(places).build();
        UserContext ctx = UserContext.builder()
                .userId(TEST_USER_ID).preferences(context.getPreferences()).locationData(locationData).build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{visitedPlacesText}} / {{todayActivityGuide}}")
                .build();
        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(ctx);

        // Then: 외출 섹션은 없고, 집에서 어떻게 지냈는지 묻는 가이드가 들어간다
        assertThat(result).contains("[집에서 보낸 시간]");
        assertThat(result).doesNotContain("[외출한 곳]");
        assertThat(result).contains("집에서 어떻게 지내셨는지");
        assertThat(result).doesNotContain("장소를 언급하며");
        assertThat(result).doesNotContain("장소를 절대 언급하지 말고");
    }

    @Test
    @DisplayName("buildSystemPrompt - 집 체류가 야간뿐이면 활동 대상에서 제외되어 일반 질문 가이드로 폴백")
    void buildSystemPrompt_nighttimeHomeExcluded() {
        // Given: 야간 집 체류만 (23:00~23:59) → 낮 체류 아님 → 제외
        List<VisitedPlace> places = List.of(
                VisitedPlace.builder().placeName("자택").isHome(true)
                        .visitStartTime(LocalTime.of(23, 0)).visitEndTime(LocalTime.of(23, 59))
                        .stayDurationMinutes(59).build()
        );
        LocationData locationData = LocationData.builder().currentCity("서울").visitedPlaces(places).build();
        UserContext ctx = UserContext.builder()
                .userId(TEST_USER_ID).preferences(context.getPreferences()).locationData(locationData).build();

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("{{todayActivityGuide}}")
                .build();
        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(ctx);

        // Then: 낮 집 체류도 외출도 없으므로 장소 언급 없는 일반 질문 가이드
        assertThat(result).contains("장소를 절대 언급하지 말고");
    }

    // ===== {{recallGuide}} 치환 테스트 =====

    @Test
    @DisplayName("buildSystemPrompt - 전달한 recallGuide가 {{recallGuide}} 자리에 치환됨")
    void buildSystemPrompt_recallGuideSubstituted() {
        // Given
        String recallGuide = "[오늘의 회상 주제] 고향\n[발굴 모드] 이 주제로는 아직 들려주신 이야기가 없습니다.";

        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("주제: {{recallGuide}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When
        String result = promptService.buildSystemPrompt(context, List.of(), recallGuide);

        // Then
        assertThat(result).contains(recallGuide);
        assertThat(result).doesNotContain("{{recallGuide}}");
    }

    @Test
    @DisplayName("buildSystemPrompt - recallGuide가 null이어도 플레이스홀더가 새지 않고 폴백 문구로 치환됨")
    void buildSystemPrompt_nullRecallGuide_fallbackText() {
        // Given
        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.SYSTEM)
                .content("주제: {{recallGuide}}")
                .build();

        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.SYSTEM))
                .thenReturn(Optional.of(template));

        // When: recallGuide 없이 호출하는 오버로드 (buildRecallGuide가 null을 반환한 경우와 동일)
        String result = promptService.buildSystemPrompt(context, List.of());

        // Then
        assertThat(result).doesNotContain("{{recallGuide}}");
        assertThat(result).contains("오늘 지정된 회상 주제가 없습니다");
    }

    // ===== buildRecallGuide 테스트 =====

    @Test
    @DisplayName("buildRecallGuide - topic이 null이면 null 반환")
    void buildRecallGuide_nullTopic() {
        assertThat(promptService.buildRecallGuide(null, List.of())).isNull();
    }

    @Test
    @DisplayName("buildRecallGuide - topic이 blank이면 null 반환")
    void buildRecallGuide_blankTopic() {
        assertThat(promptService.buildRecallGuide("   ", List.of())).isNull();
    }

    @Test
    @DisplayName("buildRecallGuide - 저장된 기억이 없으면 발굴 모드")
    void buildRecallGuide_emptyMemories_discoveryMode() {
        String guide = promptService.buildRecallGuide("고향", List.of());

        assertThat(guide).contains("[오늘의 회상 주제] 고향");
        assertThat(guide).contains("[발굴 모드]");
    }

    @Test
    @DisplayName("buildRecallGuide - 같은 topic의 기억이 없으면(다른 주제만 있음) 발굴 모드")
    void buildRecallGuide_noMatchingTopic_discoveryMode() {
        List<Memory> memories = List.of(
                Memory.builder().userId(TEST_USER_ID).lifePeriod("청년기").topic("일")
                        .content("30대에 부산에서 어부로 일했다").build());

        String guide = promptService.buildRecallGuide("고향", memories);

        assertThat(guide).contains("[발굴 모드]");
    }

    @Test
    @DisplayName("buildRecallGuide - 같은 topic의 기억이 있으면 심화 모드로 그 내용을 포함")
    void buildRecallGuide_matchingTopic_deepeningMode() {
        List<Memory> memories = List.of(
                Memory.builder().userId(TEST_USER_ID).lifePeriod("유년기").topic("고향")
                        .content("전라도 시골 마을에서 자랐다").build());

        String guide = promptService.buildRecallGuide("고향", memories);

        assertThat(guide).contains("[오늘의 회상 주제] 고향");
        assertThat(guide).contains("[심화 모드]");
        assertThat(guide).contains("전라도 시골 마을에서 자랐다");
    }

    @Test
    @DisplayName("buildRecallGuide - MEMORY v1 시절의 옛 topic 어휘만 저장돼 있으면 발굴 모드로 폴백")
    void buildRecallGuide_legacyTopicVocabulary_fallsBackToDiscoveryMode() {
        // Given: MEMORY v2 이전 어휘(가족/직업/장소/사건/취미/습관/기타)로 저장된 기억
        // 다음 대화 종료 시 extractAndSaveMemories가 새 어휘로 재분류(self-healing)하기 전 상태
        List<Memory> legacyMemories = List.of(
                Memory.builder().userId(TEST_USER_ID).lifePeriod("청년기").topic("직업")
                        .content("30대에 부산에서 어부로 일했다").build(),
                Memory.builder().userId(TEST_USER_ID).lifePeriod("중년기").topic("가족")
                        .content("손주 이름은 민준이다").build());

        // When: 오늘의 회상 주제가 새 카탈로그의 "일"이어도 옛 어휘 "직업"과는 매칭되지 않음
        String guide = promptService.buildRecallGuide("일", legacyMemories);

        // Then: 예외 없이 발굴 모드로 안전하게 떨어져야 함
        assertThat(guide).contains("[오늘의 회상 주제] 일");
        assertThat(guide).contains("[발굴 모드]");
        assertThat(guide).doesNotContain("[심화 모드]");
    }

    @Test
    @DisplayName("buildRecallGuide - 기억 목록이 null이어도 예외 없이 발굴 모드")
    void buildRecallGuide_nullMemories_discoveryMode() {
        String guide = promptService.buildRecallGuide("고향", null);

        assertThat(guide).contains("[발굴 모드]");
    }

    // ===== buildMemoryPrompt 테스트 =====

    @Test
    @DisplayName("buildMemoryPrompt - {{topicVocabulary}}가 RecallTopicRotationService.TOPICS로 치환됨")
    void buildMemoryPrompt_topicVocabularySubstituted() {
        PromptTemplate template = PromptTemplate.builder()
                .type(PromptType.MEMORY)
                .content("허용 주제: {{topicVocabulary}}")
                .build();
        when(promptTemplateRepository.findFirstByTypeAndIsActiveTrueOrderByCreatedAtDesc(PromptType.MEMORY))
                .thenReturn(Optional.of(template));

        String result = promptService.buildMemoryPrompt(context, List.of());

        assertThat(result).contains("고향").contains("나들이");
        assertThat(result).doesNotContain("{{topicVocabulary}}");
    }

}
