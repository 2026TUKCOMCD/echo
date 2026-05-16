package com.example.echo.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LocationUpdateRequest {
    @NotNull(message = "거주 지역은 필수입니다.")
    @Size(max = 100)
    private String location;
}
