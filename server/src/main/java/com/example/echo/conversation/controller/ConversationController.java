package com.example.echo.conversation.controller;

import com.example.echo.common.auth.CurrentUser;
import com.example.echo.conversation.dto.ConversationEndResponse;
import com.example.echo.conversation.dto.ConversationResponse;
import com.example.echo.conversation.dto.ConversationStartRequest;
import com.example.echo.conversation.dto.ConversationStartResponse;
import com.example.echo.conversation.dto.StreamMeta;
import com.example.echo.conversation.dto.StreamedConversation;
import com.example.echo.conversation.dto.TtsRetryResponse;
import com.example.echo.conversation.exception.StreamAbortedException;
import com.example.echo.conversation.service.ConversationService;
import com.example.echo.conversation.stream.ConversationStreamWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 대화 처리 컨트롤러
 */
@Slf4j
@Tag(name = "Conversation", description = "AI 음성 대화 API")
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    @Operation(
            summary = "대화 시작",
            description = "AI가 먼저 인사하며 대화를 시작합니다. 건강 데이터와 위치 데이터를 함께 전송하면 맞춤형 인사를 생성합니다."
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
            @RequestBody(required = false) ConversationStartRequest request
    ) {
        logStartRequest(userId, request, "대화 시작 요청");

        ConversationStartResponse response = conversationService.startConversation(
                userId,
                request != null ? request.getHealthData() : null,
                request != null ? request.getLocationData() : null
        );
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "대화 시작 (스트리밍 응답)",
            description = "/start와 같은 처리(컨텍스트 초기화 → 첫 인사 생성 → TTS)를 하되, TTS 전체 합성을 기다리지 않고 "
                    + "오디오를 생성되는 대로 내려보냅니다. 응답 규격은 /message-stream과 같은 application/x-echo-stream "
                    + "프레임 스트림이며, 첫 인사에는 사용자 발화가 없으므로 META의 userMessage는 항상 null입니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "스트리밍 시작 (본문은 프레임 스트림)"),
            @ApiResponse(responseCode = "500", description = "AI 응답 생성 실패 또는 TTS 시작 전 처리 실패")
    })
    @PostMapping("/start-stream")
    public void startConversationStream(
            @Parameter(hidden = true) @CurrentUser Long userId,
            @RequestBody(required = false) ConversationStartRequest request,
            HttpServletResponse response
    ) throws IOException {
        logStartRequest(userId, request, "대화 시작 요청 (스트리밍)");

        // 컨텍스트 초기화/첫 인사 생성/TTS 첫 바이트 확인까지는 응답을 건드리지 않는다 - 여기서 실패하면 기존과 같은 JSON 오류 응답이 나간다.
        StreamedConversation result = conversationService.startConversationStream(
                userId,
                request != null ? request.getHealthData() : null,
                request != null ? request.getLocationData() : null
        );
        writeStream(result, userId, response);
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
            summary = "음성 메시지 전송 (스트리밍 응답)",
            description = "/message와 같은 처리(STT → AI → TTS)를 하되, TTS 전체 합성을 기다리지 않고 오디오를 생성되는 대로 "
                    + "내려보냅니다. 응답은 application/x-echo-stream 프레임 스트림입니다: "
                    + "[1바이트 type][4바이트 big-endian 길이][payload] 프레임이 META(0x01, JSON) → AUDIO(0x02, mp3 조각)... → "
                    + "END(0x00) 순서로 옵니다. END 없이 끝나면 클라이언트는 잘린 스트림으로 보고 /tts-retry로 폴백해야 합니다."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "스트리밍 시작 (본문은 프레임 스트림)"),
            @ApiResponse(responseCode = "400", description = "음성 파일 형식 오류"),
            @ApiResponse(responseCode = "500", description = "STT/AI/TTS 시작 전 처리 실패")
    })
    @PostMapping(value = "/message-stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public void processMessageStream(
            @Parameter(hidden = true) @CurrentUser Long userId,
            @Parameter(description = "음성 파일 (WAV, MP3, M4A 지원)", required = true)
            @RequestPart("audio") MultipartFile audioFile,
            HttpServletResponse response
    ) throws IOException {
        // STT/LLM 처리와 TTS 첫 바이트 확인까지는 응답을 건드리지 않는다 - 여기서 실패하면 기존과 같은 JSON 오류 응답이 나간다.
        StreamedConversation result = conversationService.processUserMessageStream(userId, audioFile);
        writeStream(result, userId, response);
    }

    /** 대화 시작 요청의 입력 데이터 상세 로그 (/start, /start-stream 공통) */
    private void logStartRequest(Long userId, ConversationStartRequest request, String label) {
        log.info("=== {} - userId: {} ===", label, userId);
        if (request != null && request.getLocationData() != null) {
            var loc = request.getLocationData();
            log.debug("[입력] 현재좌표: ({}, {}), 총이동거리: {}km",
                    loc.getCurrentLatitude(), loc.getCurrentLongitude(), loc.getTotalDistanceKm());
            if (loc.getVisitedPlaces() != null) {
                log.info("[입력] 방문장소 수: {}", loc.getVisitedPlaces().size());
                loc.getVisitedPlaces().forEach(place ->
                    log.debug("[입력] 방문장소 - 좌표: ({}, {}), 시작: {}, 종료: {}, 체류: {}분",
                            place.getLatitude(), place.getLongitude(),
                            place.getVisitStartTime(), place.getVisitEndTime(),
                            place.getStayDurationMinutes()));
            }
        } else {
            log.info("[입력] 위치 데이터 없음");
        }
    }

    /**
     * 프레임 스트림 응답 쓰기 (/message-stream, /start-stream 공통).
     *
     * StreamingResponseBody 대신 응답에 직접 쓴다: 비동기 재-dispatch가 없어 JWT 필터/보안 설정과 얽히지 않는다.
     */
    private void writeStream(StreamedConversation result, Long userId, HttpServletResponse response) throws IOException {
        try (InputStream audio = result.audioStream()) {
            byte[] meta = objectMapper.writeValueAsBytes(new StreamMeta(result.userMessage(), result.aiResponse()));

            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(ConversationStreamWriter.CONTENT_TYPE);
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
            // nginx가 이 응답만 버퍼링하지 않고 청크를 즉시 전달하도록 한다 (nginx 설정 변경 불필요)
            response.setHeader("X-Accel-Buffering", "no");

            ConversationStreamWriter.Result outcome =
                    ConversationStreamWriter.write(response.getOutputStream(), meta, audio);
            switch (outcome) {
                case COMPLETED -> result.onStreamCompleted().run();
                case UPSTREAM_FAILED -> log.error("TTS 스트림이 중간에 끊겨 END 없이 종료 - userId: {}", userId);
                case CLIENT_DISCONNECTED -> log.info("클라이언트가 연결을 끊어 스트리밍을 중단 - userId: {}", userId);
            }
        } catch (RuntimeException e) {
            // 응답이 이미 시작됐다면 오류 본문을 쓸 수 없으므로 전용 예외로 바꿔 조용히 종료시킨다
            if (response.isCommitted()) {
                throw new StreamAbortedException("스트리밍 응답 도중 예상치 못한 실패 - userId: " + userId, e);
            }
            throw e;
        }
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
        ConversationEndResponse response = conversationService.endConversation(userId);
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
