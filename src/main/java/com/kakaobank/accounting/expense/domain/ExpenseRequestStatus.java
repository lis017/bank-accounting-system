package com.kakaobank.accounting.expense.domain;

import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;

import java.util.EnumSet;
import java.util.Map;

/**
 * 집행 요청 상태와 허용된 전이 정의.
 *
 * 금융회계에서 상태전이는 곧 감사 대상이므로, 잘못된 전이가 절대 발생하지 않도록
 * "어떤 상태에서 어떤 상태로 갈 수 있는가"를 enum 자체에 박아둡니다.
 *
 * 면접 포인트:
 *   - 단순 if/switch로 흩어 두면 새 상태가 추가될 때 누락이 생깁니다.
 *   - "허용된 다음 상태 집합" 데이터를 enum 내부에 두면 컴파일러가 우리 편이 되어줍니다.
 *   - validateTransitionTo는 도메인 규칙 위반을 즉시 BusinessException으로 변환합니다.
 */
public enum ExpenseRequestStatus {

    DRAFT,
    REQUESTED,
    APPROVED,
    EXECUTED,
    CLOSED,
    REJECTED,
    EXECUTION_FAILED;

    private static final Map<ExpenseRequestStatus, EnumSet<ExpenseRequestStatus>> ALLOWED_NEXT = Map.of(
            DRAFT,             EnumSet.of(REQUESTED, REJECTED),
            REQUESTED,         EnumSet.of(APPROVED, REJECTED),
            // 승인된 요청은 실행되거나, 외부 ERP에서 실패할 수 있습니다.
            APPROVED,          EnumSet.of(EXECUTED, EXECUTION_FAILED),
            // 실행된 요청은 일마감 배치로 CLOSED 처리됩니다.
            EXECUTED,          EnumSet.of(CLOSED),
            // 실패 건은 retry로 다시 EXECUTED 시도, 또는 영구 거절될 수 있습니다.
            EXECUTION_FAILED,  EnumSet.of(EXECUTED, REJECTED),
            CLOSED,            EnumSet.noneOf(ExpenseRequestStatus.class),
            REJECTED,          EnumSet.noneOf(ExpenseRequestStatus.class)
    );

    public void validateTransitionTo(ExpenseRequestStatus next) {
        EnumSet<ExpenseRequestStatus> allowed = ALLOWED_NEXT.getOrDefault(this, EnumSet.noneOf(ExpenseRequestStatus.class));
        if (!allowed.contains(next)) {
            throw new BusinessException(
                    ErrorCode.INVALID_STATUS_TRANSITION,
                    "허용되지 않는 상태전이입니다. 현재=" + this + ", 다음=" + next);
        }
    }
}
