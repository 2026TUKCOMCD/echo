package com.example.echo.diary.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.diary.dto.DiaryResponse;
import com.example.echo.diary.entity.Diary;
import com.example.echo.diary.service.DiaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 일기 조회 컨트롤러
 *
 * 일기 생성은 대화 종료(/api/conversations/end) 시 자동으로 수행됨
 */
@Slf4j
@Tag(name = "Diary", description = "일기 조회 API")
@RestController
@RequestMapping("/api/diaries")
@RequiredArgsConstructor
public class DiaryController {

    private final DiaryService diaryService;

    @Operation(
            summary = "일기 목록 조회",
            description = "최근 N일의 일기를 날짜 내림차순으로 조회합니다. 생성 실패 기록(status=FAILED)도 포함됩니다. "
                    + "startDate/endDate를 모두 지정하면 days 대신 해당 날짜 범위(양끝 포함)로 조회합니다(캘린더 월 조회용)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DiaryResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "endDate가 startDate보다 이전")
    })
    @GetMapping
    public ResponseEntity<List<DiaryResponse>> getDiaries(
            @Parameter(hidden = true) @CurrentUser Long userId,
            @Parameter(description = "조회 기간 (오늘 포함 최근 N일). startDate/endDate가 함께 오면 무시됩니다.", example = "30")
            @RequestParam(defaultValue = "30") int days,
            @Parameter(description = "조회 시작일 (yyyy-MM-dd, endDate와 함께 지정 시 사용)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일 (yyyy-MM-dd, startDate와 함께 지정 시 사용)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        List<Diary> result = (startDate != null && endDate != null)
                ? diaryService.getDiariesInRange(userId, startDate, endDate)
                : diaryService.getDiaries(userId, days);
        List<DiaryResponse> diaries = result.stream()
                .map(DiaryResponse::from)
                .toList();
        return ResponseEntity.ok(diaries);
    }

    @Operation(summary = "일기 단건 조회")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DiaryResponse.class))
            ),
            @ApiResponse(responseCode = "404", description = "일기 없음")
    })
    @GetMapping("/{id}")
    public ResponseEntity<DiaryResponse> getDiary(
            @Parameter(hidden = true) @CurrentUser Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(DiaryResponse.from(diaryService.getDiary(id, userId)));
    }
}
