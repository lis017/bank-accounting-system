package com.kakaobank.accounting.idempotency.domain;

/**
 * 멱등성 키 상태.
 *
 * - PROCESSING: 첫 요청을 받아 처리 중 (재요청 시 충돌로 응답)
 * - COMPLETED: 처리 완료 (재요청 시 저장된 응답 그대로 반환)
 * - FAILED: 처리 실패 (재요청 시 같은 실패 응답 반환 또는 재시도 허용 정책에 따라 결정)
 */
public enum IdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}
