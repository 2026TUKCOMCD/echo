package com.example.echo.diary.service;

import com.example.echo.diary.entity.Diary;

/**
 * DiaryService.generateAndSaveDiary()의 결과
 *
 * - Skipped: 사용자 발화가 없어 일기 생성을 의도적으로 건너뜀 (일기 없음)
 * - Processed: 일기 생성을 시도함. diary.getStatus()가 SUCCESS 또는 FAILED이며,
 *   실패 기록 저장 자체가 실패한 경우 diary.getId()는 null일 수 있음
 *
 * "스킵"과 "실패"가 둘 다 null로 뭉뚱그려지던 것을 타입으로 구분하기 위해 도입.
 */
public sealed interface DiaryOutcome {

    record Skipped() implements DiaryOutcome {}

    record Processed(Diary diary) implements DiaryOutcome {}
}
