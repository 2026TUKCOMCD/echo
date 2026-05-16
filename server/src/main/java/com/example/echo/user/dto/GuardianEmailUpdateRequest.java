package com.example.echo.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GuardianEmailUpdateRequest {
    @Email(message = "보호자 이메일 형식이 올바르지 않습니다.")
    @Size(max = 255)
    private String guardianEmail;
}
