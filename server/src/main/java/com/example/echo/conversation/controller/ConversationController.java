package com.example.echo.conversation.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.conversation.dto.ConversationEndResponse;
import com.example.echo.conversation.dto.ConversationResponse;
import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.conversation.dto.TtsRetryResponse;
import com.example.echo.conversation.service.ConversationService;
import com.example.echo.health.dto.HealthData;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;

/**
 * 대화 처리 컨트롤러
 */
@Tag(name = "Conversation", description = "AI 음성 대화 API")
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @Operation(
            summary = "대화 시작",
            description = "AI가 먼저 인사하며 대화를 시작합니다. 건강 데이터를 함께 전송하면 맞춤형 인사를 생성합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "대화 시작 성공",
                    content = @Content(schema = @Schema(implementation = ConversationStartResponse.class))
            ),
            @ApiResponse(responseCode = "500", description = "AI 응답 생성 실패")
    })
    @PostMapping("/start")
    public ResponseEntity<ConversationStartResponse> startConversation(
            @Parameter(hidden = true) @CurrentUser Long userId,
            @RequestBody(required = false) HealthData healthData
    ) {
        ConversationStartResponse response = conversationService.startConversation(userId, healthData);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "음성 메시지 전송",
            description = "사용자 음성 파일을 전송하면 STT → AI 응답 → TTS 처리 후 응답합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "메시지 처리 성공",
                    content = @Content(schema = @Schema(implementation = ConversationResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "음성 파일 형식 오류"),
            @ApiResponse(responseCode = "500", description = "STT/AI/TTS 처리 실패")
    })
    @PostMapping(value = "/message", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ConversationResponse> processMessage(
            @Parameter(hidden = true) @CurrentUser Long userId,
            @Parameter(description = "음성 파일 (WAV, MP3, M4A 지원)", required = true)
            @RequestPart("audio") MultipartFile audioFile
    ) {
        ConversationResponse response = conversationService.processUserMessage(userId, audioFile);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "대화 종료",
            description = "대화를 종료하고 일기를 생성합니다. 컨텍스트가 정리됩니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "대화 종료 성공",
                    content = @Content(schema = @Schema(implementation = ConversationEndResponse.class))
            )
    })
    @PostMapping("/end")
    public ResponseEntity<ConversationEndResponse> endConversation(
            @Parameter(hidden = true) @CurrentUser Long userId
    ) {
        conversationService.endConversation(userId);
        ConversationEndResponse response = ConversationEndResponse.builder()
                .endedAt(LocalDateTime.now())
                .build();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "TTS 재시도",
            description = "마지막 AI 응답의 TTS를 재생성합니다. 네트워크 오류 등으로 음성을 받지 못한 경우 사용합니다."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "TTS 재생성 성공",
                    content = @Content(schema = @Schema(implementation = TtsRetryResponse.class))
            ),
            @ApiResponse(responseCode = "404", description = "재시도할 대화 기록 없음")
    })
    @PostMapping("/tts-retry")
    public ResponseEntity<TtsRetryResponse> retryTts(
            @Parameter(hidden = true) @CurrentUser Long userId
    ) {
        TtsRetryResponse response = conversationService.retryTts(userId);
        return ResponseEntity.ok(response);
    }
}
