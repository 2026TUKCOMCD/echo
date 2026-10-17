package com.example.echo.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 미래 날짜 생일이 저장되면 나이가 음수로 계산돼 대화 프롬프트에 들어간다.
 * 요청 DTO의 @Valid 검증 실패는 GlobalExceptionHandler가 400으로 응답한다.
 */
@DisplayName("생년월일 검증 - 미래 날짜를 받지 않는다")
class BirthdayValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("PUT /preferences/birthday - 미래 날짜는 검증 실패")
    void birthdayUpdate_rejectsFutureDate() {
        Set<ConstraintViolation<BirthdayUpdateRequest>> violations =
                validator.validate(new BirthdayUpdateRequest(LocalDate.now().plusDays(2)));

        assertThat(violations).extracting(v -> v.getPropertyPath().toString()).containsExactly("birthday");
    }

    @Test
    @DisplayName("PUT /preferences/birthday - 과거 날짜는 통과")
    void birthdayUpdate_acceptsPastDate() {
        assertThat(validator.validate(new BirthdayUpdateRequest(LocalDate.of(1950, 3, 1)))).isEmpty();
    }

    @Test
    @DisplayName("PUT /preferences (전체 설정) - 미래 날짜 생일은 검증 실패")
    void preferencesUpdate_rejectsFutureBirthday() {
        UserPreferences request = validPreferences().birthday(LocalDate.now().plusDays(2)).build();

        assertThat(validator.validate(request))
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("birthday");
    }

    @Test
    @DisplayName("PUT /preferences (전체 설정) - 과거 날짜 생일은 통과")
    void preferencesUpdate_acceptsPastBirthday() {
        assertThat(validator.validate(validPreferences().build())).isEmpty();
    }

    /** 생일 외 필수 항목을 채운 요청 - 검증 대상이 생일 하나가 되도록 */
    private static UserPreferences.UserPreferencesBuilder validPreferences() {
        return UserPreferences.builder()
                .birthday(LocalDate.of(1950, 3, 1))
                .location("서울시 강남구")
                .conversationTime(LocalTime.of(10, 0))
                .preferredSleepHours(7);
    }
}
