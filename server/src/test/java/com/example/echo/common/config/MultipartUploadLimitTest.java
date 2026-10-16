package com.example.echo.common.config;

import com.example.echo.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.MultipartAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * application.yaml의 multipart 한도를 실제 서블릿 컨테이너(내장 Tomcat)로 확인한다.
 * MockMultipartFile·MockMvc는 컨테이너의 multipart 파싱을 거치지 않아 크기 한도를 재현하지 못한다.
 */
@SpringBootTest(classes = MultipartUploadLimitTest.UploadApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("multipart 업로드 한도 - 음성 파일 업로드")
class MultipartUploadLimitTest {

    /** 앱의 발화당 최대 길이(60초)를 16kHz 16-bit 모노 WAV로 보낸 크기 (헤더 44바이트 + PCM) */
    private static final int MAX_UTTERANCE_WAV_BYTES = 44 + 16_000 * 2 * 60;

    @SpringBootConfiguration
    @ImportAutoConfiguration({
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            MultipartAutoConfiguration.class
    })
    @Import({UploadController.class, GlobalExceptionHandler.class})
    static class UploadApp {
    }

    @RestController
    static class UploadController {
        @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        public String upload(@RequestPart("audio") MultipartFile audio) {
            return String.valueOf(audio.getSize());
        }
    }

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("앱의 발화당 최대 길이(60초) WAV 업로드가 서블릿 컨테이너에서 거절되지 않는다")
    void maxUtteranceWav_isAccepted() throws IOException, InterruptedException {
        HttpResponse<String> response = postMultipart(new byte[MAX_UTTERANCE_WAV_BYTES]);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo(String.valueOf(MAX_UTTERANCE_WAV_BYTES));
    }

    private HttpResponse<String> postMultipart(byte[] audio) throws IOException, InterruptedException {
        String boundary = "echo-test-boundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"audio\"; filename=\"recording.wav\"\r\n"
                + "Content-Type: audio/wav\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(audio);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/upload"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
