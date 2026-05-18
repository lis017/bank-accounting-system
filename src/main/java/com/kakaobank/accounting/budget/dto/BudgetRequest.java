package com.kakaobank.accounting.budget.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record BudgetRequest(
        @NotNull Long departmentId,
        @NotNull Long accountCodeId,
        @NotNull Integer fiscalYear,
        @NotNull @Min(1) Long allocatedAmount,
        @NotNull LocalDate validFrom,
        @NotNull LocalDate validTo
) { }
