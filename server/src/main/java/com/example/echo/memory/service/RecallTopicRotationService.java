/*
 * 장기기억 topic 어휘
 *
 * 원래는 날짜 기반으로 오늘의 회상 주제를 고르는 서비스였으나, SYSTEM v14부터 회상 주제를 어르신 말씀에서
 * 뽑게 되어 주제 선택(currentTopic)은 쓰이지 않다가 RAG3(기억 검색 전환)에서 제거했다.
 *
 * TOPICS는 MEMORY 프롬프트의 topic 허용 어휘({{topicVocabulary}})의 단일 출처로 남는다.
 */
package com.example.echo.memory.service;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RecallTopicRotationService {

    public static final List<String> TOPICS = List.of(
            "고향", "학창시절", "음식", "일", "결혼", "친구",
            "부모형제", "자녀", "노래", "드라마", "취미", "나들이"
    );
}
