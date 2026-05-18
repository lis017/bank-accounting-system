package com.kakaobank.accounting.expense.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ExpenseRequestCreateRequest(
        @NotNull Long budgetId,
        @NotNull Long departmentId,
        @NotNull Long accountCodeId,
        @NotNull @Min(1) Long amount,
        @Size(max = 200) String memo,
        @NotNull LocalDate targetDate
) { }
