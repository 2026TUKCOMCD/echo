/*
 * 장기기억 회상 주제(3단계) 매일 로테이션
 *
 * 선택 방식: 날짜 기반 stateless 선택 (ModelRotationService와 동일 패턴)
 * - 인메모리 인덱스를 두지 않음 -> 같은 날이면 세션 수·서버 재시작과 무관하게 항상 같은 주제
 * - 매일 같은 주제(예: 축구)만 반복되던 문제(양의 피드백 루프)를 근본적으로 차단하기 위한 장치
 *
 * TOPICS는 MEMORY 프롬프트의 topic 허용 어휘와 반드시 일치해야 한다 (단일 출처).
 * 어긋나면 오늘 topic과 저장된 Memory.topic의 매칭이 조용히 실패해 항상 발굴 모드로만 빠진다.
 */
package com.example.echo.memory.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecallTopicRotationService {

    public static final List<String> TOPICS = List.of(
            "고향", "학창시절", "음식", "일", "결혼", "친구",
            "부모형제", "자녀", "노래", "드라마", "취미", "나들이"
    );

    private final Clock clock;

    /**
     * 오늘 사용할 장기기억 회상 주제를 반환한다.
     */
    public String currentTopic() {
        long day = LocalDate.now(clock).toEpochDay();
        int index = Math.floorMod(day, TOPICS.size());
        return TOPICS.get(index);
    }
}
