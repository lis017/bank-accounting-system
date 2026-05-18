package com.kakaobank.accounting.common.exception;

import com.kakaobank.accounting.common.filter.RequestIdFilter;
import com.kakaobank.accounting.common.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * 전역 예외 처리기.
 *
 * 책임:
 *   - 도메인 예외(BusinessException)는 ErrorCode에 정의된 상태/코드로 매핑합니다.
 *   - @Valid 실패는 400으로 응답하면서 어느 필드가 왜 실패했는지 함께 내려줍니다.
 *   - 낙관적 락 충돌(예산 동시 차감)은 409로 응답해, 클라이언트가 재시도 여부를 결정하도록 합니다.
 *   - 알 수 없는 예외는 500으로 응답하되, 운영 로그에는 스택트레이스를 남겨 디버깅을 가능하게 합니다.
 *
 * 면접 포인트:
 *   - Controller마다 try/catch를 작성하면 일관성이 깨지고 누락이 생깁니다.
 *   - 한 곳에서 매핑을 관리하면 정책 변경이 쉽고, 응답 포맷의 일관성이 보장됩니다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        // 비즈니스 예외는 정상 흐름의 일부일 수 있으므로 WARN으로 남깁니다(스택트레이스 미포함).
        log.warn("BusinessException: code={}, message={}", errorCode.getCode(), ex.getMessage());
        ErrorResponse body = ErrorResponse.of(errorCode.getCode(), ex.getMessage(), currentRequestId());
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new ErrorResponse.FieldError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage()))
                .toList();
        ErrorResponse body = ErrorResponse.of(
                ErrorCode.INVALID_REQUEST.getCode(),
                ErrorCode.INVALID_REQUEST.getDefaultMessage(),
                currentRequestId(),
                fieldErrors);
        log.warn("ValidationException: fields={}", fieldErrors);
        return ResponseEntity.status(ErrorCode.INVALID_REQUEST.getStatus()).body(body);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        // 예산 동시 차감 등에서 발생합니다. 클라이언트가 재시도 가능한 상황입니다.
        log.warn("OptimisticLockConflict: {}", ex.getMessage());
        ErrorCode errorCode = ErrorCode.BUDGET_LOCK_CONFLICT;
        ErrorResponse body = ErrorResponse.of(errorCode.getCode(), errorCode.getDefaultMessage(), currentRequestId());
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception ex) {
        // 예상하지 못한 예외는 운영에서 반드시 발견되어야 하므로 ERROR + 스택트레이스로 남깁니다.
        log.error("Unhandled exception", ex);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        ErrorResponse body = ErrorResponse.of(errorCode.getCode(), errorCode.getDefaultMessage(), currentRequestId());
        return ResponseEntity.status(errorCode.getStatus()).body(body);
    }

    private String currentRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_REQUEST_ID);
        return requestId == null ? "N/A" : requestId;
    }
}
