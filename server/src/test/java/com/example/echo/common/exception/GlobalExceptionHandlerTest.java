package com.example.echo.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GlobalExceptionHandler - 존재하지 않는 경로 처리")
class GlobalExceptionHandlerTest {

    @RestController
    static class ThrowingController {
        @GetMapping("/no-resource")
        public String noResource() throws NoResourceFoundException {
            // 매핑되지 않은 경로 요청 시 Spring(ResourceHttpRequestHandler)이 던지는 예외와 동일
            throw new NoResourceFoundException(HttpMethod.POST, "api/conversations/message-stream");
        }

        @GetMapping("/unexpected")
        public String unexpected() {
            throw new IllegalStateException("boom");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("NoResourceFoundException은 catch-all(500)이 아니라 404로 응답한다")
    void noResourceFound_returns404() throws Exception {
        mockMvc.perform(get("/no-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("NOT_FOUND")));
    }

    @Test
    @DisplayName("그 외 예상치 못한 예외는 기존대로 500으로 응답한다")
    void unexpectedException_stillReturns500() throws Exception {
        mockMvc.perform(get("/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("INTERNAL_SERVER_ERROR")));
    }
}
