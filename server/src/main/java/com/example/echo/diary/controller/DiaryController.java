package com.example.echo.diary.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.diary.dto.DiaryResponse;
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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
            description = "최근 N일의 일기를 날짜 내림차순으로 조회합니다. 생성 실패 기록(status=FAILED)도 포함됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "조회 성공",
                    content = @Content(schema = @Schema(implementation = DiaryResponse.class))
            )
    })
    @GetMapping
    public ResponseEntity<List<DiaryResponse>> getDiaries(
            @Parameter(hidden = true) @CurrentUser Long userId,
            @Parameter(description = "조회 기간 (오늘 포함 최근 N일)", example = "30")
            @RequestParam(defaultValue = "30") int days
    ) {
        List<DiaryResponse> diaries = diaryService.getDiaries(userId, days).stream()
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
