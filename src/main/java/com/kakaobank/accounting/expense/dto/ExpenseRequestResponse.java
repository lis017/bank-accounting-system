package com.kakaobank.accounting.expense.dto;

import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;

import java.time.LocalDate;

public record ExpenseRequestResponse(
        Long expenseRequestId,
        Long budgetId,
        Long departmentId,
        Long accountCodeId,
        Long amount,
        String memo,
        ExpenseRequestStatus status,
        LocalDate targetDate,
        Integer retryCount,
        Long journalEntryId
) {
    public static ExpenseRequestResponse of(ExpenseRequest entity, Long journalEntryId) {
        return new ExpenseRequestResponse(
                entity.getId(),
                entity.getBudgetId(),
                entity.getDepartmentId(),
                entity.getAccountCodeId(),
                entity.getAmount(),
                entity.getMemo(),
                entity.getStatus(),
                entity.getTargetDate(),
                entity.getRetryCount(),
                journalEntryId
        );
    }
}
