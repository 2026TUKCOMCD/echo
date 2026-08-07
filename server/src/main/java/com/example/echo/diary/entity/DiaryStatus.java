package com.example.echo.diary.entity;

/**
 * 일기 생성 상태
 *
 * - SUCCESS: 일기 생성 성공 (content 존재)
 * - FAILED: 마지막 일기 생성 시도 실패 (이전 성공본 content가 남아있을 수 있음)
 */
public enum DiaryStatus {
    SUCCESS,
    FAILED
}
