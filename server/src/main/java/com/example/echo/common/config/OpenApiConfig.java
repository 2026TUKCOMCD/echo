package com.example.echo.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI(Swagger) 설정
 *
 * API 문서 접근 경로:
 * - Swagger UI: http://localhost:8080/swagger-ui.html
 * - OpenAPI JSON: http://localhost:8080/v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("로컬 개발 서버")
                ));
    }

    private Info apiInfo() {
        return new Info()
                .title("Echo API")
                .description("경도인지장애 분들의 치매 예방을 위한 AI 음성 대화 시스템 API")
                .version("1.0.0")
                .contact(new Contact()
                        .name("2026TUKCOMCD")
                        .url("https://github.com/2026TUKCOMCD/echo"));
    }
}
