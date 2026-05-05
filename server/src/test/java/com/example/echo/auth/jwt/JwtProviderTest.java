package com.example.echo.auth.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class JwtProviderTest {

    private static final String SECRET = "test-secret-key-for-jwt-provider-unit-test-must-be-long-enough-256bit";
    private static final long ACCESS_EXP_MS = 3600_000L;       // 1h
    private static final long REFRESH_EXP_MS = 2_592_000_000L; // 30d

    private JwtProvider jwtProvider;

    @BeforeEach
    void setUp() {
        jwtProvider = new JwtProvider(SECRET, ACCESS_EXP_MS, REFRESH_EXP_MS);
        ReflectionTestUtils.invokeMethod(jwtProvider, "init");
    }

    @Test
    void access_token_round_trip() {
        Long userId = 42L;
        String token = jwtProvider.generateAccessToken(userId);

        assertThat(jwtProvider.validate(token)).isTrue();
        assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
        assertThat(jwtProvider.getExpiration(token)).isAfter(new Date());
    }

    @Test
    void refresh_token_round_trip() {
        Long userId = 7L;
        String token = jwtProvider.generateRefreshToken(userId);

        assertThat(jwtProvider.validate(token)).isTrue();
        assertThat(jwtProvider.getUserId(token)).isEqualTo(userId);
    }

    @Test
    void refresh_token_expires_later_than_access_token() {
        String access = jwtProvider.generateAccessToken(1L);
        String refresh = jwtProvider.generateRefreshToken(1L);

        assertThat(jwtProvider.getExpiration(refresh))
                .isAfter(jwtProvider.getExpiration(access));
    }

    @Test
    void invalid_token_returns_false() {
        assertThat(jwtProvider.validate("not-a-real-token")).isFalse();
        assertThat(jwtProvider.validate("")).isFalse();
    }

    @Test
    void token_signed_with_other_secret_fails_validation() {
        JwtProvider other = new JwtProvider(
                "another-secret-key-of-sufficient-length-for-hmac-sha-256-algorithm",
                ACCESS_EXP_MS,
                REFRESH_EXP_MS
        );
        ReflectionTestUtils.invokeMethod(other, "init");

        String tokenFromOther = other.generateAccessToken(1L);
        assertThat(jwtProvider.validate(tokenFromOther)).isFalse();
    }
}
