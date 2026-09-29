package com.example.echo.conversation.live;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 실시간 음성 메시지 WebSocket(/api/conversations/message-live) 등록.
 *
 * 인증: 핸드셰이크도 일반 HTTP 요청이라 기존 JwtAuthFilter(Authorization 헤더)와 SecurityConfig(anyRequest 인증)를
 * 그대로 거친다. 여기서는 인증된 userId를 세션 속성으로 옮기기만 한다. 토큰을 URL 쿼리에 싣지 않는다(접근 로그에 남음).
 *
 * Origin: 허용 목록을 따로 열지 않는다 - 기본값(같은 출처만)이라 다른 사이트의 브라우저 스크립트가 사용자 대신 연결하는
 * 것을 막고, Origin 헤더를 보내지 않는 앱(OkHttp)은 그대로 통과한다.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class LiveMessageWebSocketConfig implements WebSocketConfigurer {

    public static final String PATH = "/api/conversations/message-live";

    private final LiveMessageHandler liveMessageHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveMessageHandler, PATH)
                .addInterceptors(new AuthenticatedUserInterceptor());
    }

    /** 보안 필터가 인증한 userId를 WebSocket 세션 속성으로 옮긴다 */
    static class AuthenticatedUserInterceptor implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !(authentication.getPrincipal() instanceof Long userId)) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(LiveMessageHandler.ATTR_USER_ID, userId);
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }
    }
}
