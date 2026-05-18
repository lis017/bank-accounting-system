package com.kakaobank.accounting.integration.erp;

public record ErpPaymentResponse(
        boolean success,
        String externalReferenceId,
        String failureReason
) {
    public static ErpPaymentResponse success(String externalReferenceId) {
        return new ErpPaymentResponse(true, externalReferenceId, null);
    }

    public static ErpPaymentResponse failure(String failureReason) {
        return new ErpPaymentResponse(false, null, failureReason);
    }
}
