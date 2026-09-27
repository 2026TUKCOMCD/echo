package com.example.echo.memory.service;

import com.example.echo.context.domain.ConversationTurn;
import com.example.echo.context.domain.UserContext;
import com.example.echo.location.dto.LocationData;
import com.example.echo.location.dto.VisitedPlace;
import com.example.echo.memory.config.MemoryRecallProperties;
import com.example.echo.memory.entity.Memory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemoryRecallServiceTest {

    private static final Long USER_ID = 1L;

    @Mock
    private MemoryService memoryService;

    private MemoryRecallService memoryRecallService;

    @BeforeEach
    void setUp() {
        MemoryRecallProperties properties = new MemoryRecallProperties();
        properties.setThreshold(0.5);
        properties.setTopK(2);
        properties.setSkipUserTurns(1);
        properties.setBackchannels(Set.of("응", "네", "그렇지", "몰라"));
        memoryRecallService = new MemoryRecallService(memoryService, properties);
    }

    private UserContext contextWith(ConversationTurn... turns) {
        UserContext context = UserContext.builder().userId(USER_ID).build();
        context.getConversationHistory().addAll(List.of(turns));
        return context;
    }

    private ConversationTurn turn(String userMessage, String aiResponse) {
        return ConversationTurn.builder().userMessage(userMessage).aiResponse(aiResponse).build();
    }

    private Memory memory(long id, String content) {
        Memory memory = Memory.builder().userId(USER_ID).lifePeriod("유년기").topic("부모형제").content(content).build();
        ReflectionTestUtils.setField(memory, "id", id);
        return memory;
    }

    // ===== 발화 게이트·질의 =====

    @Test
    @DisplayName("첫 사용자 발화(날씨·안부에 대한 대답)는 검색하지 않는다 - 첫 AI 인사는 발화 수에 세지 않는다")
    void turnQuery_skipsFirstUserTurn() {
        UserContext context = contextWith(turn(null, "지금 날씨가 맑네요. 잘 주무셨어요?"));

        assertThat(memoryRecallService.turnQuery(context, "응 잘 잤어, 아침에 시장도 다녀왔어")).isEmpty();
    }

    @Test
    @DisplayName("두 번째 발화부터는 직전 AI 발화와 어르신 발화를 이어 질의로 만든다")
    void turnQuery_joinsPreviousAiResponseAndUserMessage() {
        UserContext context = contextWith(
                turn(null, "지금 날씨가 맑네요. 잘 주무셨어요?"),
                turn("응 잘 잤어", "오늘 시장에 다녀오셨네요. 거기서 어떤 일 보셨어요?"));

        assertThat(memoryRecallService.turnQuery(context, "떡집 지나니까 엄마 생각이 나더라고"))
                .contains("오늘 시장에 다녀오셨네요. 거기서 어떤 일 보셨어요?\n떡집 지나니까 엄마 생각이 나더라고");
    }

    @Test
    @DisplayName("맞장구는 공백·문장부호를 빼고 목록과 같으면 검색하지 않는다")
    void turnQuery_skipsBackchannels() {
        UserContext context = contextWith(turn(null, "인사"), turn("응", "질문"));

        assertThat(memoryRecallService.turnQuery(context, "네~")).isEmpty();
        assertThat(memoryRecallService.turnQuery(context, " 그렇지. ")).isEmpty();
        assertThat(memoryRecallService.turnQuery(context, "몰라!")).isEmpty();
    }

    @Test
    @DisplayName("짧아도 맞장구가 아니면 검색한다 - 글자 수로 거르면 짧은 고유명사를 함께 버린다")
    void turnQuery_keepsShortProperNouns() {
        UserContext context = contextWith(turn(null, "인사"), turn("응", "고향이 어디세요?"));

        assertThat(memoryRecallService.turnQuery(context, "부산")).contains("고향이 어디세요?\n부산");
    }

    @Test
    @DisplayName("무음 턴(사용자 발화 null)은 발화 수에 세지 않는다")
    void turnQuery_doesNotCountSilentTurns() {
        UserContext context = contextWith(turn(null, "인사"), turn(null, "죄송해요, 잘 못 들었어요."));

        assertThat(memoryRecallService.turnQuery(context, "응 잘 잤어")).isEmpty();
    }

    // ===== 대화 시작 질의 =====

    @Test
    @DisplayName("대화 시작 질의는 집을 뺀 외출 장소 이름만 쉼표로 잇고, 루틴 장소는 카테고리 라벨을 쓴다")
    void greetingQuery_joinsOutingPlaceNames() {
        UserContext context = UserContext.builder().userId(USER_ID).locationData(LocationData.builder()
                .visitedPlaces(List.of(
                        VisitedPlace.builder().placeName("우리집").isHome(true).build(),
                        VisitedPlace.builder().placeName("자갈치시장").build(),
                        VisitedPlace.builder().placeName("서울대학교병원").routineCategory("병원").build(),
                        VisitedPlace.builder().build(),
                        VisitedPlace.builder().placeName("자갈치시장").build()))
                .build()).build();

        assertThat(memoryRecallService.greetingQuery(context)).contains("자갈치시장, 병원");
    }

    @Test
    @DisplayName("외출 기록이 없으면(위치 없음·집만 있음) 대화 시작 검색을 하지 않는다")
    void greetingQuery_emptyWithoutOutings() {
        UserContext noLocation = UserContext.builder().userId(USER_ID).build();
        UserContext homeOnly = UserContext.builder().userId(USER_ID).locationData(LocationData.builder()
                .visitedPlaces(List.of(VisitedPlace.builder().placeName("우리집").isHome(true).build()))
                .build()).build();

        assertThat(memoryRecallService.greetingQuery(noLocation)).isEmpty();
        assertThat(memoryRecallService.greetingQuery(homeOnly)).isEmpty();
    }

    // ===== 기억 블록 누적 =====

    @Test
    @DisplayName("새로 찾은 기억은 블록에 덧붙고, 이미 붙은 기억은 검색에서 빼며 블록에서 지우지 않는다")
    void recallInto_accumulatesAndExcludesAlreadyRecalled() {
        UserContext context = contextWith();
        Memory earlier = memory(16, "부산 영도에서 태어났다");
        Memory found = memory(18, "어머니가 시장에서 떡 장사를 하셨다");
        context.getRecalledMemories().add(earlier);
        when(memoryService.search(eq(USER_ID), eq("질의"), eq(Set.of(16L)), eq(2), eq(0.5))).thenReturn(List.of(found));

        List<Memory> added = memoryRecallService.recallInto(context, "질의");

        assertThat(added).containsExactly(found);
        assertThat(context.getRecalledMemories()).containsExactly(earlier, found);
    }

    @Test
    @DisplayName("검색이 빈손이면(실패 포함) 블록에 이미 있는 기억은 그대로 남는다")
    void recallInto_keepsBlockWhenNothingFound() {
        UserContext context = contextWith();
        Memory earlier = memory(18, "어머니가 시장에서 떡 장사를 하셨다");
        context.getRecalledMemories().add(earlier);
        when(memoryService.search(eq(USER_ID), eq("질의"), eq(Set.of(18L)), eq(2), eq(0.5))).thenReturn(List.of());

        memoryRecallService.recallInto(context, "질의");

        assertThat(context.getRecalledMemories()).containsExactly(earlier);
        verify(memoryService).search(USER_ID, "질의", Set.of(18L), 2, 0.5);
    }
}
