package com.example.echo.auth.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}
