package com.kakaobank.accounting.integration.erp;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ErpPaymentRequest(
        @NotNull Long expenseRequestId,
        @NotNull @Min(1) Long amount,
        @NotBlank String memo
) { }
