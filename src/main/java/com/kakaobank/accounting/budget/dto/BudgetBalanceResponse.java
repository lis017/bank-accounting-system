package com.kakaobank.accounting.budget.dto;

public record BudgetBalanceResponse(
        Long budgetId,
        Long allocatedAmount,
        Long remainingAmount
) { }
