package com.kakaobank.accounting.accounting.domain;

/**
 * 회계전표 라인의 차/대변 구분.
 *
 * 회계의 기본 원칙(복식부기)은 한 전표 안에서
 *   차변(DEBIT) 합계 = 대변(CREDIT) 합계
 * 를 반드시 만족해야 한다는 것입니다. 이 원칙은 JournalEntry.validateBalanced에서 검증됩니다.
 */
public enum DebitCreditType {
    DEBIT,
    CREDIT
}
