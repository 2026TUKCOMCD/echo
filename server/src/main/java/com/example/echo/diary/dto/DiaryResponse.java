package com.example.echo.diary.dto;

import com.example.echo.diary.entity.Diary;
import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "일기 응답")
@Getter
@Builder
public class DiaryResponse {

    @Schema(description = "일기 ID", example = "1")
    private Long id;

    @Schema(description = "일기 날짜", example = "2026-07-20")
    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @Schema(description = "일기 제목", example = "7월 20일의 일기")
    private String title;

    @Schema(description = "일기 본문 (생성 실패 시 null이거나 이전 성공본)")
    private String content;

    @Schema(description = "생성 상태 (SUCCESS/FAILED)", example = "SUCCESS")
    private String status;

    @Schema(description = "마지막 생성 실패 사유 (성공 시 null)")
    private String failureReason;

    @Schema(description = "그날 날씨", example = "맑음")
    private String weather;

    @Schema(description = "기분 (현재 미사용)", example = "null")
    private String mood;

    @Schema(description = "최초 생성 시각")
    private LocalDateTime createdAt;

    @Schema(description = "마지막 갱신 시각")
    private LocalDateTime updatedAt;

    public static DiaryResponse from(Diary diary) {
        return DiaryResponse.builder()
                .id(diary.getId())
                .date(diary.getDiaryDate())
                .title(diary.getTitle())
                .content(diary.getContent())
                .status(diary.getStatus().name())
                .failureReason(diary.getFailureReason())
                .weather(diary.getWeather())
                .mood(diary.getMood())
                .createdAt(diary.getCreatedAt())
                .updatedAt(diary.getUpdatedAt())
                .build();
    }
}
