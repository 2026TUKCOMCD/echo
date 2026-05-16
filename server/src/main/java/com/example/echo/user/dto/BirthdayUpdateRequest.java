package com.example.echo.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BirthdayUpdateRequest {
    @NotNull(message = "생년월일은 필수입니다.")
    private LocalDate birthday;
}
