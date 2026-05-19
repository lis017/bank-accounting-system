package com.kakaobank.accounting.expense.service;

import com.kakaobank.accounting.budget.service.BudgetService;
import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 집행 사전 준비(상태 검증 + 예산 차감)를 담당하는 Service.
 *
 * ExpenseExecutionService와 분리한 이유 — self-invocation 방지:
 *   Spring @Transactional은 CGLIB 프록시로 동작하므로, 같은 클래스 내부에서
 *   this.methodA()로 호출하면 프록시를 우회해 @Transactional이 적용되지 않습니다.
 *   이 클래스를 별도 Spring 빈으로 분리함으로써 ExpenseExecutionService가
 *   프록시를 통해 호출하게 되어, "상태 검증 + 예산 차감"이 단일 트랜잭션으로 보장됩니다.
 *
 * 트랜잭션 설계:
 *   - getEntityOrThrow(readOnly=true)는 외부 트랜잭션에 참여합니다.
 *   - decreaseBalance(@Transactional)도 이 트랜잭션에 참여하므로
 *     상태 검증과 예산 차감이 원자적으로 커밋/롤백됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpensePreparationService {

    private final ExpenseRequestService expenseRequestService;
    private final BudgetService budgetService;

    /**
     * APPROVED 상태 검증 후 예산을 차감합니다.
     *
     * 실패 시 호출자(ExpenseExecutionService)가 catch해 멱등성 키를 FAILED로 마킹합니다.
     */
    @Transactional
    public void prepareExecution(Long expenseRequestId) {
        ExpenseRequest entity = expenseRequestService.getEntityOrThrow(expenseRequestId);
        if (entity.getStatus() != ExpenseRequestStatus.APPROVED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "execute는 APPROVED 상태에서만 가능합니다. 현재=" + entity.getStatus());
        }
        budgetService.decreaseBalance(entity.getBudgetId(), entity.getAmount());
    }
}
