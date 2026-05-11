package com.example.echo.user.service;

import com.example.echo.auth.exception.UnauthorizedException;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.entity.User;
import com.example.echo.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserPreferences getPreferences(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("사용자를 찾을 수 없습니다."));

        return UserPreferences.builder()
                .userId(user.getId())
                .name(user.getName())
                .build();
    }
}
