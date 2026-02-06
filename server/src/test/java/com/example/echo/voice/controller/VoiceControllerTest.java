package com.example.echo.voice.controller;

import com.example.echo.user.dto.VoiceSettings;
import com.example.echo.voice.dto.TtsRequest;
import com.example.echo.voice.exception.VoiceProcessingException;
import com.example.echo.voice.service.VoiceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VoiceController.class)
class VoiceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VoiceService voiceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("POST /api/voice/stt - STT 엔드포인트")
    class SttEndpointTest {

        @Test
        @DisplayName("정상 요청: 음성 파일 → 200 OK + 텍스트 응답")
        void success() throws Exception {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "file", "test.mp3", "audio/mpeg", "fake-audio".getBytes()
            );

            when(voiceService.speechToText(any())).thenReturn("안녕하세요");

            mockMvc.perform(multipart("/api/voice/stt").file(audioFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.text").value("안녕하세요"));
        }

        @Test
        @DisplayName("VoiceProcessingException 발생 시 예외가 전파된다 (GlobalExceptionHandler 미구현)")
        void voiceProcessingException_propagates() {
            MockMultipartFile audioFile = new MockMultipartFile(
                    "file", "test.mp3", "audio/mpeg", "fake-audio".getBytes()
            );

            when(voiceService.speechToText(any()))
                    .thenThrow(new VoiceProcessingException("오디오 파일이 비어있습니다."));

            assertThatThrownBy(() ->
                    mockMvc.perform(multipart("/api/voice/stt").file(audioFile))
            ).rootCause().isInstanceOf(VoiceProcessingException.class);
        }
    }

    @Nested
    @DisplayName("POST /api/voice/tts - TTS 엔드포인트")
    class TtsEndpointTest {

        @Test
        @DisplayName("정상 요청: 텍스트 → 200 OK + audio/mpeg 응답")
        void success() throws Exception {
            TtsRequest request = new TtsRequest("안녕하세요",
                    VoiceSettings.builder().voiceSpeed(1.0).voiceTone("warm").build());
            byte[] audioData = "fake-mp3-data".getBytes();

            when(voiceService.textToSpeech(any(), any())).thenReturn(audioData);

            mockMvc.perform(post("/api/voice/tts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "audio/mpeg"))
                    .andExpect(header().string("Content-Disposition", "inline; filename=\"speech.mp3\""))
                    .andExpect(content().bytes(audioData));
        }

        @Test
        @DisplayName("VoiceSettings 없이 텍스트만 전달해도 정상 처리")
        void withoutVoiceSettings_success() throws Exception {
            TtsRequest request = new TtsRequest("안녕하세요", null);
            byte[] audioData = "audio".getBytes();

            when(voiceService.textToSpeech(any(), any())).thenReturn(audioData);

            mockMvc.perform(post("/api/voice/tts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "audio/mpeg"));
        }

        @Test
        @DisplayName("VoiceProcessingException 발생 시 예외가 전파된다 (GlobalExceptionHandler 미구현)")
        void voiceProcessingException_propagates() {
            TtsRequest request = new TtsRequest("", null);

            when(voiceService.textToSpeech(any(), any()))
                    .thenThrow(new VoiceProcessingException("변환할 텍스트가 비어있습니다."));

            assertThatThrownBy(() ->
                    mockMvc.perform(post("/api/voice/tts")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            ).rootCause().isInstanceOf(VoiceProcessingException.class);
        }
    }
}
