package com.example.echo.conversation.controller;

import com.example.echo.common.auth.CurrentUserArgumentResolver;
import com.example.echo.common.exception.GlobalExceptionHandler;
import com.example.echo.conversation.config.ConversationStreamProperties;
import com.example.echo.conversation.dto.StreamedConversation;
import com.example.echo.conversation.service.ConversationService;
import com.example.echo.conversation.stream.ConversationStreamWriter;
import com.example.echo.conversation.stream.SpeechSegment;
import com.example.echo.conversation.stream.TestSpeechSegments;
import com.example.echo.conversation.stream.TestSpeechSegments.Frame;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.example.echo.conversation.stream.TestSpeechSegments.parseFrames;
import static com.example.echo.conversation.stream.TestSpeechSegments.segment;
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

    @BeforeEach
    void setUp() {
        ConversationController controller = new ConversationController(conversationService, objectMapper, new ConversationStreamProperties());
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

    @Test
    @DisplayName("성공: META → TEXT → AUDIO → TEXT → AUDIO → END 프레임으로 응답하고, 대화 내용은 헤더에 싣지 않는다")
    void success_streamsFrames_andKeepsConversationOutOfHeaders() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        TestSpeechSegments segments = TestSpeechSegments.of(
                segment("반가워요, 어르신! 오늘 산책은 어떠셨어요?", "mp3-1"),
                segment("날씨가 좋아서 걷기 좋으셨겠어요.", "mp3-2"));
        when(conversationService.processUserMessageStream(eq(USER_ID), any())).thenReturn(
                new StreamedConversation("안녕하세요", segments, () -> completed.set(true)));

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
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
        JsonNode meta = objectMapper.readTree(frames.get(0).payload());
        assertThat(meta.get("userMessage").asText()).isEqualTo("안녕하세요");
        assertThat(meta.has("aiResponse")).as("AI 응답은 META가 아니라 TEXT 프레임으로 온다").isFalse();
        assertThat(objectMapper.readTree(frames.get(1).payload()).get("text").asText())
                .isEqualTo("반가워요, 어르신! 오늘 산책은 어떠셨어요?");
        assertThat(frames.get(2).text()).isEqualTo("mp3-1");
        assertThat(objectMapper.readTree(frames.get(3).payload()).get("text").asText())
                .isEqualTo("날씨가 좋아서 걷기 좋으셨겠어요.");
        assertThat(frames.get(4).text()).isEqualTo("mp3-2");
        assertThat(completed).as("전송을 끝까지 마치면 지연 측정 콜백이 실행됨").isTrue();
        assertThat(segments.closed).as("조각 원천은 응답을 마치면 닫힌다").isTrue();
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
        TestSpeechSegments segments = TestSpeechSegments.of(new SpeechSegment("반가워요", failingAudio));
        when(conversationService.processUserMessageStream(eq(USER_ID), any())).thenReturn(
                new StreamedConversation("안녕", segments, () -> completed.set(true)));

        MockHttpServletResponse response = mockMvc.perform(multipart(URL).file(audio()))
                .andReturn().getResponse();

        assertThat(parseFrames(response.getContentAsByteArray())).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META, ConversationStreamWriter.TYPE_TEXT, ConversationStreamWriter.TYPE_AUDIO);
        assertThat(completed).isFalse();
        assertThat(segments.closed).isTrue();
    }

    @Test
    @DisplayName("나머지 조각을 준비하지 못하면(나머지 TTS 실패) 첫 조각 뒤에 END 없이 종료한다")
    void restSegmentFails_noEndFrame() throws Exception {
        AtomicBoolean completed = new AtomicBoolean(false);
        when(conversationService.processUserMessageStream(eq(USER_ID), any())).thenReturn(
                new StreamedConversation("안녕", TestSpeechSegments.failingAfter(segment("반가워요, 어르신!", "mp3")),
                        () -> completed.set(true)));

        MockHttpServletResponse response = mockMvc.perform(multipart(URL).file(audio()))
                .andReturn().getResponse();

        assertThat(parseFrames(response.getContentAsByteArray())).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META, ConversationStreamWriter.TYPE_TEXT, ConversationStreamWriter.TYPE_AUDIO);
        assertThat(completed).isFalse();
    }

    @Test
    @DisplayName("스트림 시작 전 실패(첫 조각 TTS를 열지 못함)는 기존과 같은 JSON 오류 응답이 나간다")
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
                new StreamedConversation("안녕", TestSpeechSegments.of(segment("반가워요", new byte[]{1, 2, 3})),
                        () -> {
                            throw new IllegalStateException("측정 기록 실패");
                        }));

        MockHttpServletResponse response = mockMvc.perform(multipart(URL).file(audio()))
                .andReturn().getResponse();

        // 응답 전체가 온전한 프레임으로만 파싱돼야 한다 (JSON 오류 본문이 섞이면 파싱이 어긋난다)
        assertThat(parseFrames(response.getContentAsByteArray())).extracting(Frame::type).containsExactly(
                ConversationStreamWriter.TYPE_META,
                ConversationStreamWriter.TYPE_TEXT,
                ConversationStreamWriter.TYPE_AUDIO,
                ConversationStreamWriter.TYPE_END);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).doesNotContain("INTERNAL_SERVER_ERROR");
    }
}
