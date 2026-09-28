package com.example.echo.user.service;

import com.example.echo.auth.exception.UnauthorizedException;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.entity.User;
import com.example.echo.user.repository.UserPreferencesRepository;
import com.example.echo.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long TEST_USER_ID = 1L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserPreferencesRepository userPreferencesRepository;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("이름을 바꾸면 앞뒤 공백을 제거해 저장하고, 바뀐 이름이 담긴 사용자 정보를 반환한다")
    void updateName_trimsAndReturnsUpdatedPreferences() {
        // given
        User user = User.builder().loginId("demo").passwordHash("hash").name("김순자").build();
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
        when(userPreferencesRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.empty());

        // when
        UserPreferences result = userService.updateName(TEST_USER_ID, "  홍길동 ");

        // then
        assertThat(user.getName()).isEqualTo("홍길동");
        assertThat(result.getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("사용자가 없으면 UnauthorizedException을 던진다")
    void updateName_throwsWhenUserNotFound() {
        // given
        when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userService.updateName(TEST_USER_ID, "홍길동"))
                .isInstanceOf(UnauthorizedException.class);
    }
}
