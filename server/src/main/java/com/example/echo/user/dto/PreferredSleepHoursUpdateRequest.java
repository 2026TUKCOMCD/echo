package com.example.echo.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreferredSleepHoursUpdateRequest {
    private Integer preferredSleepHours;
}
