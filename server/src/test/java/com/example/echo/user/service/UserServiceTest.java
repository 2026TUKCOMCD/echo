package com.example.echo.user.service;

import com.example.echo.auth.exception.UnauthorizedException;
import com.example.echo.common.NonKstJvmTimeZone;
import com.example.echo.user.dto.UserPreferences;
import com.example.echo.user.entity.User;
import com.example.echo.user.repository.UserPreferencesRepository;
import com.example.echo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
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

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, userPreferencesRepository, Clock.system(ZoneId.of("Asia/Seoul")));
    }

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

    @Test
    @DisplayName("나이는 한국 날짜 기준 - 운영 서버(JVM 기본 시간대 UTC)에서도 생일 아침에 한 살 적게 계산하지 않는다")
    void getPreferences_ageUsesKstToday() {
        try (NonKstJvmTimeZone tz = NonKstJvmTimeZone.apply()) {
            // given: 한국 기준 오늘이 80번째 생일
            User user = User.builder().loginId("demo").passwordHash("hash").name("김순자").build();
            com.example.echo.user.entity.UserPreferences prefs = com.example.echo.user.entity.UserPreferences.builder()
                    .userId(TEST_USER_ID)
                    .birthday(tz.kstToday().minusYears(80))
                    .build();
            when(userRepository.findById(TEST_USER_ID)).thenReturn(Optional.of(user));
            when(userPreferencesRepository.findByUserId(TEST_USER_ID)).thenReturn(Optional.of(prefs));

            // when
            UserPreferences result = userService.getPreferences(TEST_USER_ID);

            // then
            assertThat(LocalDate.now()).as("재현 조건: JVM 날짜가 한국 날짜와 다름").isNotEqualTo(tz.kstToday());
            assertThat(result.getAge()).isEqualTo(80);
        }
    }
}
