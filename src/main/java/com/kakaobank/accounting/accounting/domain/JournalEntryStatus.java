package com.kakaobank.accounting.accounting.domain;

/**
 * 회계전표 상태.
 *
 * - POSTED: 생성 및 차변/대변 검증 완료 (정상)
 * - CANCELED: 사후 취소 (본 과제에서는 사용하지 않지만 확장 여지를 둠)
 */
public enum JournalEntryStatus {
    POSTED,
    CANCELED
}
