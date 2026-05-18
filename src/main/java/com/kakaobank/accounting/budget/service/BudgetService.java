package com.kakaobank.accounting.budget.service;

import com.kakaobank.accounting.budget.domain.AccountCode;
import com.kakaobank.accounting.budget.domain.Budget;
import com.kakaobank.accounting.budget.domain.BudgetBalance;
import com.kakaobank.accounting.budget.domain.Department;
import com.kakaobank.accounting.budget.dto.*;
import com.kakaobank.accounting.budget.repository.AccountCodeRepository;
import com.kakaobank.accounting.budget.repository.BudgetBalanceRepository;
import com.kakaobank.accounting.budget.repository.BudgetRepository;
import com.kakaobank.accounting.budget.repository.DepartmentRepository;
import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예산/부서/계정과목 등록 및 차감을 책임지는 Service.
 *
 * 트랜잭션 경계는 각 메서드 단위입니다. 읽기 메서드는 readOnly로 두어
 * JPA의 dirty checking 비용을 줄입니다.
 *
 * 예산 차감(decreaseBalance)은 별도 트랜잭션에서 수행할 수도 있지만,
 * "집행이 일어나는 트랜잭션과 한 묶음"이어야 정합성이 보장되므로
 * 호출자(ExpenseExecutionService)의 트랜잭션 안에서 실행됩니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final DepartmentRepository departmentRepository;
    private final AccountCodeRepository accountCodeRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetBalanceRepository budgetBalanceRepository;

    @Transactional
    public Long createDepartment(DepartmentRequest request) {
        Department saved = departmentRepository.save(
                Department.create(request.departmentCode(), request.departmentName()));
        return saved.getId();
    }

    @Transactional
    public Long createAccountCode(AccountCodeRequest request) {
        AccountCode saved = accountCodeRepository.save(
                AccountCode.create(request.code(), request.codeName(), request.accountType()));
        return saved.getId();
    }

    @Transactional
    public Long createBudget(BudgetRequest request) {
        // 마스터 데이터 존재 여부 사전 검증.
        if (!departmentRepository.existsById(request.departmentId())) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
        if (!accountCodeRepository.existsById(request.accountCodeId())) {
            throw new BusinessException(ErrorCode.ACCOUNT_CODE_NOT_FOUND);
        }

        Budget budget = budgetRepository.save(Budget.create(
                request.departmentId(),
                request.accountCodeId(),
                request.fiscalYear(),
                request.allocatedAmount(),
                request.validFrom(),
                request.validTo()));

        // 예산 등록과 동시에 잔액 레코드를 한 번만 생성합니다.
        // 잔액 레코드가 없으면 차감 로직이 작동할 수 없으므로 반드시 같은 트랜잭션에서 만듭니다.
        budgetBalanceRepository.save(BudgetBalance.initialize(budget.getId(), budget.getAllocatedAmount()));

        log.info("BUDGET_CREATED budgetId={} departmentId={} accountCodeId={} amount={}",
                budget.getId(), budget.getDepartmentId(), budget.getAccountCodeId(), budget.getAllocatedAmount());
        return budget.getId();
    }

    @Transactional(readOnly = true)
    public BudgetBalanceResponse getBalance(Long budgetId) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_NOT_FOUND));
        BudgetBalance balance = budgetBalanceRepository.findByBudgetId(budgetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_NOT_FOUND));
        return new BudgetBalanceResponse(budget.getId(), budget.getAllocatedAmount(), balance.getRemainingAmount());
    }

    /**
     * 예산 잔액에서 금액을 차감합니다.
     *
     * 호출자의 트랜잭션 안에서 호출되며, 별도 트랜잭션을 시작하지 않습니다.
     * 따라서 호출자가 트랜잭션을 롤백하면 차감도 함께 롤백됩니다.
     *
     * @Version 낙관적 락이 동시 차감을 방지합니다. 충돌 시
     * ObjectOptimisticLockingFailureException이 발생하고, GlobalExceptionHandler가 409로 응답합니다.
     */
    @Transactional
    public void decreaseBalance(Long budgetId, Long amount) {
        log.info("BUDGET_DECREASE_TRY budgetId={} amount={}", budgetId, amount);
        BudgetBalance balance = budgetBalanceRepository.findByBudgetId(budgetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BUDGET_NOT_FOUND));
        balance.decrease(amount);
        log.info("BUDGET_DECREASE_OK budgetId={} remaining={}", budgetId, balance.getRemainingAmount());
    }
}
