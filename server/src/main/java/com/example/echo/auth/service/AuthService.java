package com.example.echo.auth.service;

import com.example.echo.auth.dto.SignupRequest;
import com.example.echo.auth.dto.TokenResponse;
import com.example.echo.auth.entity.RefreshToken;
import com.example.echo.auth.exception.DuplicateLoginIdException;
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

        String accessToken = jwtProvider.generateAccessToken(user.getId());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        LocalDateTime refreshExpiresAt = LocalDateTime.ofInstant(
                jwtProvider.getExpiration(refreshToken).toInstant(),
                ZoneId.systemDefault()
        );
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .expiresAt(refreshExpiresAt)
                .build());

        return new TokenResponse(accessToken, refreshToken);
    }
}
