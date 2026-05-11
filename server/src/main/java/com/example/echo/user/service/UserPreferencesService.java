package com.example.echo.user.service;

import com.example.echo.user.dto.ConversationTimeRequest;
import com.example.echo.user.dto.ConversationTimeResponse;
import com.example.echo.user.entity.UserPreferences;
import com.example.echo.user.exception.UserPreferencesNotFoundException;
import com.example.echo.user.repository.UserPreferencesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPreferencesService {

    private final UserPreferencesRepository userPreferencesRepository;

    public ConversationTimeResponse getConversationTime(Long userId) {
        UserPreferences preferences = userPreferencesRepository.findById(userId)
                .orElseThrow(() -> new UserPreferencesNotFoundException(userId));

        return ConversationTimeResponse.of(preferences.getConversationTime());
    }

    @Transactional
    public ConversationTimeResponse updateConversationTime(Long userId, ConversationTimeRequest request) {
        UserPreferences preferences = userPreferencesRepository.findById(userId)
                .orElseThrow(() -> new UserPreferencesNotFoundException(userId));

        preferences.updateConversationTime(request.getConversationTime());

        return ConversationTimeResponse.of(preferences.getConversationTime());
    }
}
