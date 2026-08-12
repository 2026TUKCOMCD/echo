package com.example.echo.memory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecallTopicRotationServiceTest {

    private static final List<String> TOPICS = RecallTopicRotationService.TOPICS;

    private RecallTopicRotationService serviceAt(long epochDay) {
        Instant instant = Instant.EPOCH.plusSeconds(epochDay * 24 * 60 * 60);
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        return new RecallTopicRotationService(clock);
    }

    @Test
    @DisplayName("day 0 -> 첫 번째 회상 주제")
    void currentTopic_dayZero() {
        assertThat(serviceAt(0).currentTopic()).isEqualTo(TOPICS.get(0));
    }

    @Test
    @DisplayName("day = 주제 개수 -> 다시 첫 번째 주제로 순환 (12일 주기)")
    void currentTopic_wrapsAroundAfterFullCycle() {
        assertThat(serviceAt(TOPICS.size()).currentTopic()).isEqualTo(TOPICS.get(0));
    }

    @Test
    @DisplayName("day 3 -> 네 번째 회상 주제")
    void currentTopic_midCycleDay() {
        assertThat(serviceAt(3).currentTopic()).isEqualTo(TOPICS.get(3));
    }

    @Test
    @DisplayName("같은 날짜면 하루에 여러 번(세션) 호출해도 항상 같은 주제를 반환")
    void currentTopic_isStableForSameDate_evenAcrossMultipleSessions() {
        RecallTopicRotationService service = serviceAt(7);

        String firstSession = service.currentTopic();
        String secondSession = service.currentTopic();
        String thirdSession = service.currentTopic();

        assertThat(firstSession).isEqualTo(secondSession).isEqualTo(thirdSession);
    }

    @Test
    @DisplayName("날짜가 바뀌면 회상 주제도 바뀜")
    void currentTopic_changesWithDate() {
        assertThat(serviceAt(0).currentTopic()).isNotEqualTo(serviceAt(1).currentTopic());
    }

    @Test
    @DisplayName("12일 연속 호출하면 12개 주제를 모두 한 번씩 순환")
    void currentTopic_cyclesThroughAllTopicsOverTwelveDays() {
        List<String> seen = new java.util.ArrayList<>();
        for (long day = 0; day < TOPICS.size(); day++) {
            seen.add(serviceAt(day).currentTopic());
        }

        assertThat(seen).containsExactlyInAnyOrderElementsOf(TOPICS);
    }

    @Test
    @DisplayName("TOPICS는 12개이며 중복이 없다 (MEMORY 프롬프트 허용 어휘의 단일 출처)")
    void topics_hasTwelveDistinctEntries() {
        assertThat(TOPICS).hasSize(12).doesNotHaveDuplicates();
    }
}
