package com.example.echo.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 잘못된 요청(클라이언트 오류)이 catch-all(500)로 뭉개지지 않는지 - 500이면 앱은 "서버 문제"로 안내하고
 * 소용없는 재시도를 띄우며, 서버에는 ERROR 로그가 쌓인다.
 */
@DisplayName("GlobalExceptionHandler - 잘못된 요청은 4xx로 응답한다")
class GlobalExceptionHandlerClientErrorTest {

    record SignupBody(String loginId, String password) {
    }

    @RestController
    static class SampleController {
        @PostMapping(value = "/json", consumes = MediaType.APPLICATION_JSON_VALUE)
        public String json(@RequestBody SignupBody body) {
            return "ok";
        }

        @GetMapping("/typed")
        public String typed(@RequestParam("days") int days) {
            return "ok";
        }

        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public String upload(@RequestPart("audio") MultipartFile audio) {
            return "ok";
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new SampleController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("JSON 본문을 읽을 수 없으면 400, 응답에 요청 본문 내용을 싣지 않는다")
    void unreadableJson_returns400() throws Exception {
        mockMvc.perform(post("/json").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"loginId\": \"demo\", \"password\": \"secret-pw-123\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")))
                .andExpect(content().string(not(containsString("secret-pw-123"))));
    }

    @Test
    @DisplayName("필수 파라미터가 없으면 400")
    void missingParameter_returns400() throws Exception {
        mockMvc.perform(get("/typed"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("파라미터 타입이 맞지 않으면 400")
    void typeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/typed").param("days", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("multipart 필수 파트(audio)가 없으면 400")
    void missingMultipartPart_returns400() throws Exception {
        mockMvc.perform(multipart("/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("BAD_REQUEST")));
    }

    @Test
    @DisplayName("지원하지 않는 HTTP 메서드는 405")
    void methodNotSupported_returns405() throws Exception {
        mockMvc.perform(put("/json").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error", is("METHOD_NOT_ALLOWED")));
    }

    @Test
    @DisplayName("지원하지 않는 Content-Type은 415")
    void mediaTypeNotSupported_returns415() throws Exception {
        mockMvc.perform(post("/json").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error", is("UNSUPPORTED_MEDIA_TYPE")));
    }
}
