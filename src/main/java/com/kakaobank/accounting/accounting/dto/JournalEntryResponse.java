package com.kakaobank.accounting.accounting.dto;

import com.kakaobank.accounting.accounting.domain.DebitCreditType;
import com.kakaobank.accounting.accounting.domain.JournalEntry;
import com.kakaobank.accounting.accounting.domain.JournalEntryStatus;

import java.time.LocalDate;
import java.util.List;

public record JournalEntryResponse(
        Long journalEntryId,
        Long expenseRequestId,
        LocalDate targetDate,
        JournalEntryStatus status,
        List<LineResponse> lines
) {
    public record LineResponse(Long accountCodeId, DebitCreditType debitCreditType, Long amount) { }

    public static JournalEntryResponse from(JournalEntry entry) {
        List<LineResponse> lineResponses = entry.getLines().stream()
                .map(line -> new LineResponse(line.getAccountCodeId(), line.getDebitCreditType(), line.getAmount()))
                .toList();
        return new JournalEntryResponse(
                entry.getId(),
                entry.getExpenseRequestId(),
                entry.getTargetDate(),
                entry.getStatus(),
                lineResponses);
    }
}
