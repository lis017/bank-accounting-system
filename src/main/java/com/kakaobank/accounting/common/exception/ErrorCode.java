package com.kakaobank.accounting.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인 전반에서 공통으로 사용하는 에러 코드.
 *
 * 클라이언트와 운영자 모두에게 일관된 식별자(code)를 제공하기 위해 enum으로 관리합니다.
 * - code: 클라이언트가 분기할 수 있는 안정적인 식별자 (HTTP status만으로는 부족)
 * - status: HTTP 응답 코드
 * - defaultMessage: 응답 기본 메시지. 상세 사유는 예외 생성 시 override 가능합니다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "요청 값이 유효하지 않습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류"),

    // 예산
    BUDGET_NOT_FOUND(HttpStatus.NOT_FOUND, "BUDGET_404", "예산을 찾을 수 없습니다."),
    BUDGET_INSUFFICIENT(HttpStatus.CONFLICT, "BUDGET_409_INSUFFICIENT", "예산이 부족합니다."),
    BUDGET_LOCK_CONFLICT(HttpStatus.CONFLICT, "BUDGET_409_LOCK", "예산 동시 차감으로 충돌이 발생했습니다."),

    // 집행 요청
    EXPENSE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "EXPENSE_404", "집행 요청을 찾을 수 없습니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "EXPENSE_409_STATUS", "현재 상태에서 허용되지 않는 전이입니다."),

    // 회계전표
    JOURNAL_NOT_BALANCED(HttpStatus.UNPROCESSABLE_ENTITY, "JOURNAL_422_BALANCE", "차변과 대변 합계가 일치하지 않습니다."),

    // 멱등성
    IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "IDEMPOTENCY_409", "동일한 Idempotency-Key로 다른 요청이 들어왔습니다."),
    IDEMPOTENCY_PROCESSING(HttpStatus.CONFLICT, "IDEMPOTENCY_409_PROCESSING", "동일한 키의 요청이 처리 중입니다."),

    // 외부 ERP
    EXTERNAL_ERP_FAILED(HttpStatus.BAD_GATEWAY, "ERP_502", "외부 ERP 호출에 실패했습니다."),

    // 부서/계정과목
    DEPARTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "DEPT_404", "부서를 찾을 수 없습니다."),
    ACCOUNT_CODE_NOT_FOUND(HttpStatus.NOT_FOUND, "ACCT_404", "계정과목을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;
}
