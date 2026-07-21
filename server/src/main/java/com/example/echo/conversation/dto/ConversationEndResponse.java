package com.example.echo.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "대화 종료 응답")
@Getter
@Builder
public class ConversationEndResponse {

    @Schema(description = "대화 종료 시간", example = "2026-03-07T11:00:00")
    private LocalDateTime endedAt;

    @Schema(description = "일기 생성 결과 (SUCCESS/FAILED/SKIPPED)", example = "SUCCESS")
    private String diaryStatus;

    @Schema(description = "생성/갱신된 일기 ID (실패·스킵 시 null)", example = "1")
    private Long diaryId;

    @Schema(description = "일기 생성 실패 사유 (성공·스킵 시 null)", example = "AI 일기 생성 실패: 상태코드 401")
    private String diaryError;

    @Schema(description = "이번 대화가 기록된 일기 날짜(Asia/Seoul 기준, SKIPPED 시 null). " +
            "클라이언트가 일기 탭에서 이 대화 세션을 날짜별로 묶을 때 로컬 타임스탬프 추정 대신 이 값을 신뢰해야 함",
            example = "2026-03-07")
    private LocalDate diaryDate;
}
