package com.example.echo.user.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilyInfoUpdateRequest {
    @Size(max = 500)
    private String familyInfo;
}
