package com.example.echo.conversation.controller;

import com.example.echo.common.auth.CurrentUserArgumentResolver;
import com.example.echo.common.exception.GlobalExceptionHandler;
import com.example.echo.conversation.dto.StreamedConversation;
import com.example.echo.conversation.service.ConversationService;
import com.example.echo.conversation.stream.ConversationStreamWriter;
import com.example.echo.voice.exception.VoiceProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationController - /start-stream")
class ConversationControllerStartStreamTest {

    private static final String URL = "/api/conversations/start-stream";
    private static final Long USER_ID = 1L;
    private static final String GREETING = "안녕하세요, 어르신! 오늘 하루는 어떠셨어요?";

    @Mock
    private ConversationService conversationService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    private record Frame(int type, byte[] payload) {
    }

    @BeforeEach
    void setUp() {
        ConversationController controller = new ConversationController(conversationService, objectMapper);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new CurrentUserArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static List<Frame> parseFrames(byte[] body) {
        List<Frame> frames = new ArrayList<>();
        int pos = 0;
        while (pos < body.length) {
            int type = body[pos] & 0xFF;
            int length = ((body[pos + 1] & 0xFF) << 24) | ((body[pos + 2] & 0xFF) << 16)
                    | ((body[pos + 3] & 0xFF) << 8) | (body[pos + 4] & 0xFF);
            byte[] payload = new byte[length];
            System.arraycopy(body, pos + 5, payload, 0, length);
            frames.add(new Frame(type, payload));
            pos += 5 + length;
        }
        return frames;
    }

    @Test
    @DisplayName("성공: META → AUDIO → END 프레임으로 응답하고, 인사말은 헤더에 싣지 않는다")
    void success_streamsFrames_andKeepsGreetingOutOfHeaders() throws Exception {
        byte[] mp3 = "mp3-bytes".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean completed = new AtomicBoolean(false);
        when(conversationService.startConversationStream(eq(USER_ID), any(), any())).thenReturn(
                new StreamedConversation(null, GREETING, new ByteArrayInputStream(mp3),
                        () -> completed.set(true)));

        MockHttpServletResponse response = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"healthData\":null,\"locationData\":null}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo(ConversationStreamWriter.CONTENT_TYPE);
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getHeader("Cache-Control")).contains("no-cache");
        // 대화 내용(민감정보)이 어떤 헤더 값에도 노출되지 않아야 함
        response.getHeaderNames().forEach(name ->
                assertThat(response.getHeader(name)).doesNotContain(GREETING));

        List<Frame> frames = parseFrames(response.getContentAsByteArray());
        assertThat(frames).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
        assertThat(frames.get(1).payload()).isEqualTo(mp3);
        assertThat(completed).as("전송을 끝까지 마치면 지연 측정 콜백이 실행됨").isTrue();
    }

    @Test
    @DisplayName("첫 인사에는 사용자 발화가 없으므로 META의 userMessage는 null이다")
    void meta_userMessageIsNull() throws Exception {
        when(conversationService.startConversationStream(eq(USER_ID), any(), any())).thenReturn(
                new StreamedConversation(null, GREETING, new ByteArrayInputStream(new byte[]{1}), () -> {
                }));

        MockHttpServletResponse response = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();

        JsonNode meta = objectMapper.readTree(parseFrames(response.getContentAsByteArray()).get(0).payload());
        assertThat(meta.get("userMessage").isNull()).isTrue();
        assertThat(meta.get("aiResponse").asText()).isEqualTo(GREETING);
    }

    @Test
    @DisplayName("요청 본문이 없어도(건강·위치 데이터 미전송) 기존 /start와 같이 null로 처리한다")
    void noRequestBody_passesNulls() throws Exception {
        when(conversationService.startConversationStream(eq(USER_ID), isNull(), isNull())).thenReturn(
                new StreamedConversation(null, GREETING, new ByteArrayInputStream(new byte[]{1}), () -> {
                }));

        MockHttpServletResponse response = mockMvc.perform(post(URL)).andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        verify(conversationService).startConversationStream(eq(USER_ID), isNull(), isNull());
    }

    @Test
    @DisplayName("스트림 시작 전 실패(TTS를 열지 못함)는 기존 /start와 같은 JSON 오류 응답이 나간다")
    void failureBeforeStreaming_returnsJsonError() throws Exception {
        when(conversationService.startConversationStream(eq(USER_ID), any(), any()))
                .thenThrow(new VoiceProcessingException("TTS 실패"));

        MockHttpServletResponse response = mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("VOICE_PROCESSING_ERROR");
        assertThat(response.getContentType()).doesNotContain(ConversationStreamWriter.CONTENT_TYPE);
    }
}
