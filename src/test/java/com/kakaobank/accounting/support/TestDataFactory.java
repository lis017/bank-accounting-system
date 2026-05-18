package com.kakaobank.accounting.support;

import com.kakaobank.accounting.budget.domain.AccountCode;
import com.kakaobank.accounting.budget.domain.Budget;
import com.kakaobank.accounting.budget.domain.BudgetBalance;
import com.kakaobank.accounting.budget.domain.Department;
import com.kakaobank.accounting.budget.repository.AccountCodeRepository;
import com.kakaobank.accounting.budget.repository.BudgetBalanceRepository;
import com.kakaobank.accounting.budget.repository.BudgetRepository;
import com.kakaobank.accounting.budget.repository.DepartmentRepository;
import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;
import com.kakaobank.accounting.expense.repository.ExpenseRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 테스트에서 공통적으로 필요한 마스터 데이터를 한 번에 만들어주는 헬퍼.
 *
 * 테스트마다 setup 코드가 반복되면 변경 비용이 커지므로, 일관된 fixture를 하나의 클래스에 모았습니다.
 */
@Component
@RequiredArgsConstructor
public class TestDataFactory {

    private final DepartmentRepository departmentRepository;
    private final AccountCodeRepository accountCodeRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetBalanceRepository budgetBalanceRepository;
    private final ExpenseRequestRepository expenseRequestRepository;

    public Department createDepartment(String code) {
        return departmentRepository.save(Department.create(code, code + "팀"));
    }

    public AccountCode createAccountCode(String code, String accountType) {
        return accountCodeRepository.findByCode(code)
                .orElseGet(() -> accountCodeRepository.save(AccountCode.create(code, code + "_NAME", accountType)));
    }

    public Budget createBudget(Long departmentId, Long accountCodeId, Long allocated) {
        Budget budget = budgetRepository.save(Budget.create(
                departmentId,
                accountCodeId,
                LocalDate.now().getYear(),
                allocated,
                LocalDate.now().minusMonths(1),
                LocalDate.now().plusMonths(12)));
        budgetBalanceRepository.save(BudgetBalance.initialize(budget.getId(), allocated));
        return budget;
    }

    public ExpenseRequest createApprovedExpense(Long budgetId, Long departmentId, Long accountCodeId, Long amount) {
        ExpenseRequest request = expenseRequestRepository.save(
                ExpenseRequest.create(budgetId, departmentId, accountCodeId, amount, "memo", LocalDate.now()));
        request.changeStatus(ExpenseRequestStatus.APPROVED);
        return expenseRequestRepository.save(request);
    }
}
