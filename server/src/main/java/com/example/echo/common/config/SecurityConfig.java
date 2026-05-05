package com.example.echo.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 보안 설정 (T1.1-2 최소 버전).
 *
 * - BCryptPasswordEncoder Bean 노출 (회원가입 비번 해시용)
 * - 세션 STATELESS, CSRF/formLogin/httpBasic 비활성 (JWT 환경 준비)
 * - 모든 엔드포인트 permitAll → 기존 API 동작 유지
 *
 * T1.2-1에서 JwtAuthFilter 추가 + 패턴별 authenticated 잠금으로 강화 예정.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
