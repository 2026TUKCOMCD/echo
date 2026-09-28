package com.example.echo.memory.dto;

import com.example.echo.memory.entity.Memory;

import java.util.List;

/**
 * 병합 판단(MEMORY_MERGE)에 넘기는 새 사실 하나와 그에 붙은 기존 기억 후보
 *
 * 후보는 사실마다 따로 붙으므로, 사실과 후보를 두 목록으로 나누지 않고 한 묶음으로 넘긴다.
 * 목록에서의 위치가 AI 응답의 index가 된다.
 *
 * @param newFact    이번 대화에서 추출한 새 사실 (아직 저장 전)
 * @param candidates 임베딩 유사도로 찾은 비슷한 기존 기억 (유사도 내림차순)
 */
public record MemoryMergeItem(Memory newFact, List<Memory> candidates) {
}
