package com.kakaobank.accounting.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartmentRequest(
        @NotBlank @Size(max = 32) String departmentCode,
        @NotBlank @Size(max = 64) String departmentName
) { }
