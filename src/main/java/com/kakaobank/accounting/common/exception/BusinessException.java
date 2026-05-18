package com.kakaobank.accounting.common.exception;

import lombok.Getter;

/**
 * 도메인/비즈니스 규칙 위반을 표현하는 공통 예외.
 *
 * GlobalExceptionHandler가 이 예외 한 종류만 보면 되도록 의도적으로 단일 계층으로 두고,
 * 도메인별 세부 분류는 ErrorCode로 구분합니다.
 *
 * 면접 포인트:
 *   - 예외 계층을 너무 깊게 나누면 사용처마다 catch가 복잡해집니다.
 *   - 반대로 모든 걸 RuntimeException으로 던지면 GlobalExceptionHandler에서 분기가 어렵습니다.
 *   - "Business 1단 + ErrorCode enum" 조합이 과제 규모에 가장 적합하다고 판단했습니다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detailMessage) {
        super(detailMessage);
        this.errorCode = errorCode;
    }
}
