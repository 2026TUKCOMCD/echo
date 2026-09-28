package com.example.echo.memory.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecallTopicRotationServiceTest {

    private static final List<String> TOPICS = RecallTopicRotationService.TOPICS;

    @Test
    @DisplayName("TOPICS는 12개이며 중복이 없다 (MEMORY 프롬프트 허용 어휘의 단일 출처)")
    void topics_hasTwelveDistinctEntries() {
        assertThat(TOPICS).hasSize(12).doesNotHaveDuplicates();
    }
}
