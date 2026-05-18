package com.kakaobank.accounting.common.util;

/**
 * 로그 마스킹 유틸.
 *
 * 멱등성 키처럼 길고 어느 정도는 노출되어야 추적이 가능한 값에 대해
 * 앞 6자리만 노출하고 나머지는 *로 가립니다.
 *
 * 면접 포인트:
 *   - 멱등성 키는 클라이언트가 만든 임의 값일 수 있으므로 잠재적으로 민감할 수 있습니다.
 *   - 그러나 운영 추적을 위해 일부 가시성이 필요합니다.
 *   - 앞 6자리만 노출하는 정책은 추적 가능성과 노출 최소화의 절충안입니다.
 */
public final class LogMasker {

    private LogMasker() { }

    public static String maskIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) {
            return null;
        }
        if (idempotencyKey.length() <= 6) {
            return idempotencyKey;
        }
        return idempotencyKey.substring(0, 6) + "***";
    }
}
