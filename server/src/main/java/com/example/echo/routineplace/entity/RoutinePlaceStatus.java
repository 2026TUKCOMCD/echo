package com.example.echo.routineplace.entity;

/**
 * 루틴 방문 장소의 확인 상태.
 *
 * - SUGGESTED: 시스템이 반복 패턴을 감지해 후보로 제안한 상태 (아직 사용자 확인 전)
 * - CONFIRMED: 사용자가 카테고리 라벨과 함께 확정한 상태 (대화에 실제로 쓰임)
 * - DISMISSED: 사용자가 "아니에요"로 거절한 상태 (같은 좌표는 영구적으로 다시 제안하지 않음)
 */
public enum RoutinePlaceStatus {
    SUGGESTED,
    CONFIRMED,
    DISMISSED
}
