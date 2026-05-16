package com.example.echo.auth.service;

import com.example.echo.auth.dto.LoginRequest;
import com.example.echo.auth.dto.SignupRequest;
import com.example.echo.auth.dto.TokenResponse;
import com.example.echo.auth.entity.RefreshToken;
import com.example.echo.auth.exception.DuplicateLoginIdException;
import com.example.echo.auth.exception.InvalidCredentialsException;
import com.example.echo.auth.exception.InvalidTokenException;
import com.example.echo.auth.jwt.JwtProvider;
import com.example.echo.auth.repository.RefreshTokenRepository;
import com.example.echo.user.entity.User;
import com.example.echo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new DuplicateLoginIdException("이미 사용 중인 아이디입니다.");
        }

        User user = userRepository.save(User.builder()
                .loginId(request.loginId())
                .passwordHash(passwordEncoder.encode(request.password()))
                .name(request.name())
                .build());

        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByLoginId(request.loginId())
                .orElseThrow(() -> new InvalidCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        refreshTokenRepository.deleteByUserId(user.getId());
        return issueTokens(user.getId());
    }

    @Transactional
    public TokenResponse refresh(String refreshToken) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new InvalidTokenException("유효하지 않은 Refresh Token입니다."));

        if (stored.isExpired() || !jwtProvider.validate(refreshToken)) {
            refreshTokenRepository.deleteByToken(refreshToken);
            throw new InvalidTokenException("Refresh Token이 만료되었습니다.");
        }

        Long userId = stored.getUserId();
        refreshTokenRepository.deleteByToken(refreshToken);
        return issueTokens(userId);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
    }

    private TokenResponse issueTokens(Long userId) {
        String accessToken = jwtProvider.generateAccessToken(userId);
        String refreshToken = jwtProvider.generateRefreshToken(userId);

        LocalDateTime refreshExpiresAt = LocalDateTime.ofInstant(
                jwtProvider.getExpiration(refreshToken).toInstant(),
                ZoneId.systemDefault()
        );
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(userId)
                .token(refreshToken)
                .expiresAt(refreshExpiresAt)
                .build());

        return new TokenResponse(accessToken, refreshToken);
    }
}
