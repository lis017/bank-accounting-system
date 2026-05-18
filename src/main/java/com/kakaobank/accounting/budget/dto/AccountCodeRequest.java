package com.kakaobank.accounting.budget.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AccountCodeRequest(
        @NotBlank @Size(max = 32) String code,
        @NotBlank @Size(max = 64) String codeName,
        @NotBlank @Size(max = 16) String accountType
) { }
