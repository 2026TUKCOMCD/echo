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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConversationController - /message-stream")
class ConversationControllerStreamTest {

    private static final String URL = "/api/conversations/message-stream";
    private static final Long USER_ID = 1L;

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

    private static MockMultipartFile audio() {
        return new MockMultipartFile("audio", "test.wav", "audio/wav", "wav".getBytes());
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
    @DisplayName("성공: META → AUDIO → END 프레임으로 응답하고, 대화 내용은 헤더에 싣지 않는다")
    void success_streamsFrames_andKeepsConversationOutOfHeaders() throws Exception {
        byte[] mp3 = "mp3-bytes".getBytes(StandardCharsets.UTF_8);
        AtomicBoolean completed = new AtomicBoolean(false);
        when(conversationService.processUserMessageStream(eq(USER_ID), any())).thenReturn(
                new StreamedConversation("안녕하세요", "반가워요, 어르신!", new ByteArrayInputStream(mp3),
                        () -> completed.set(true)));

        MockHttpServletResponse response = mockMvc.perform(multipart(URL).file(audio()))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).isEqualTo(ConversationStreamWriter.CONTENT_TYPE);
        assertThat(response.getHeader("X-Accel-Buffering")).isEqualTo("no");
        assertThat(response.getHeader("Cache-Control")).contains("no-cache");
        // 대화 내용(민감정보)이 어떤 헤더 값에도 노출되지 않아야 함
        response.getHeaderNames().forEach(name ->
                assertThat(response.getHeader(name)).doesNotContain("안녕하세요").doesNotContain("반가워요"));

        List<Frame> frames = parseFrames(response.getContentAsByteArray());
        assertThat(frames).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
        JsonNode meta = objectMapper.readTree(frames.get(0).payload());
        assertThat(meta.get("userMessage").asText()).isEqualTo("안녕하세요");
        assertThat(meta.get("aiResponse").asText()).isEqualTo("반가워요, 어르신!");
        assertThat(frames.get(1).payload()).isEqualTo(mp3);
        assertThat(completed).as("전송을 끝까지 마치면 지연 측정 콜백이 실행됨").isTrue();
    }

    @Test
    @DisplayName("TTS 스트림이 중간에 끊기면 END 프레임 없이 종료하고 완료 콜백도 실행하지 않는다")
    void upstreamFailsMidway_noEndFrame_noCompletionCallback() throws Exception {
        InputStream failingAudio = new InputStream() {
            private int calls = 0;

            @Override
            public int read() {
                throw new UnsupportedOperationException();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                if (calls++ == 0) {
                    b[off] = 7;
                    return 1;
                }
                throw new IOException("upstream reset");
            }
        };
        AtomicBoolean completed = new AtomicBoolean(false);
        when(conversationService.processUserMessageStream(eq(USER_ID), any())).thenReturn(
                new StreamedConversation("안녕", "반가워요", failingAudio, () -> completed.set(true)));

        MockHttpServletResponse response = mockMvc.perform(multipart(URL).file(audio()))
                .andReturn().getResponse();

        List<Frame> frames = parseFrames(response.getContentAsByteArray());
        assertThat(frames).extracting(Frame::type)
                .containsExactly(ConversationStreamWriter.TYPE_META, ConversationStreamWriter.TYPE_AUDIO);
        assertThat(completed).isFalse();
    }

    @Test
    @DisplayName("스트림 시작 전 실패(TTS를 열지 못함)는 기존과 같은 JSON 오류 응답이 나간다")
    void failureBeforeStreaming_returnsJsonError() throws Exception {
        when(conversationService.processUserMessageStream(eq(USER_ID), any()))
                .thenThrow(new VoiceProcessingException("TTS 실패"));

        MockHttpServletResponse response = mockMvc.perform(multipart(URL).file(audio()))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("VOICE_PROCESSING_ERROR");
        assertThat(response.getContentType()).doesNotContain(ConversationStreamWriter.CONTENT_TYPE);
    }

    @Test
    @DisplayName("스트림이 시작된 뒤 예상 못한 예외가 나도 오디오 뒤에 JSON 오류 본문을 덧붙이지 않는다")
    void failureAfterStreamingStarted_doesNotAppendErrorBody() throws Exception {
        when(conversationService.processUserMessageStream(eq(USER_ID), any())).thenReturn(
                new StreamedConversation("안녕", "반가워요", new ByteArrayInputStream(new byte[]{1, 2, 3}),
                        () -> {
                            throw new IllegalStateException("측정 기록 실패");
                        }));

        MockHttpServletResponse response = mockMvc.perform(multipart(URL).file(audio()))
                .andReturn().getResponse();

        // 응답 전체가 온전한 프레임으로만 파싱돼야 한다 (JSON 오류 본문이 섞이면 파싱이 어긋난다)
        List<Frame> frames = parseFrames(response.getContentAsByteArray());
        assertThat(frames).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("INTERNAL_SERVER_ERROR");
    }
}
