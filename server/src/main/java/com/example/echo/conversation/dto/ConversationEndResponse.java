package com.example.echo.conversation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

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
}
