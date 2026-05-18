package com.kakaobank.accounting.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 모든 에러 응답에 공통으로 사용되는 포맷.
 *
 * - code: ErrorCode enum 값 (클라이언트가 분기 시 의존하는 안정적 식별자)
 * - message: 사람이 읽을 수 있는 메시지
 * - requestId: 운영 로그와 매칭 가능한 추적 ID (MDC와 동일 값)
 * - validationErrors: @Valid 실패 시 필드별 에러 목록
 *
 * 클래스 자체는 immutable record로 두어, 응답 생성 후 상태가 바뀌지 않도록 보장합니다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String code,
        String message,
        String requestId,
        LocalDateTime occurredAt,
        List<FieldError> validationErrors
) {
    public static ErrorResponse of(String code, String message, String requestId) {
        return new ErrorResponse(code, message, requestId, LocalDateTime.now(), null);
    }

    public static ErrorResponse of(String code, String message, String requestId, List<FieldError> validationErrors) {
        return new ErrorResponse(code, message, requestId, LocalDateTime.now(), validationErrors);
    }

    public record FieldError(String field, String reason) { }
}
