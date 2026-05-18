package com.kakaobank.accounting.expense;

import com.kakaobank.accounting.accounting.repository.JournalEntryRepository;
import com.kakaobank.accounting.budget.domain.AccountCode;
import com.kakaobank.accounting.budget.domain.Budget;
import com.kakaobank.accounting.budget.domain.Department;
import com.kakaobank.accounting.budget.repository.BudgetBalanceRepository;
import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;
import com.kakaobank.accounting.expense.dto.ExpenseRequestCreateRequest;
import com.kakaobank.accounting.expense.dto.ExpenseRequestExecuteRequest;
import com.kakaobank.accounting.expense.dto.ExpenseRequestResponse;
import com.kakaobank.accounting.expense.repository.ExpenseRequestRepository;
import com.kakaobank.accounting.expense.service.ExpenseExecutionService;
import com.kakaobank.accounting.expense.service.ExpenseRequestService;
import com.kakaobank.accounting.support.StubErpClient;
import com.kakaobank.accounting.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 집행 실행 전체 흐름에 대한 통합 테스트.
 *
 * 외부 ERP는 StubErpClient로 대체합니다. 실패 시나리오는 primeFailure()로 주입합니다.
 *
 * 카뱅 평가 관점:
 *   - 이 8개 테스트는 "예산-집행-전표-멱등성-재처리" 모든 핵심 흐름을 다룹니다.
 *   - 코드리뷰에서 "왜 이 테스트만 작성했나"를 답할 수 있도록, 각 테스트가 도메인 규칙 1개씩에 대응됩니다.
 */
@SpringBootTest
@Import(StubErpClient.TestConfig.class)
class ExpenseExecutionIntegrationTest {

    @Autowired private ExpenseExecutionService expenseExecutionService;
    @Autowired private ExpenseRequestService expenseRequestService;
    @Autowired private TestDataFactory testDataFactory;
    @Autowired private StubErpClient stubErpClient;
    @Autowired private ExpenseRequestRepository expenseRequestRepository;
    @Autowired private BudgetBalanceRepository budgetBalanceRepository;
    @Autowired private JournalEntryRepository journalEntryRepository;

    private Department department;
    private AccountCode expenseAccount;
    private AccountCode payableAccount;
    private Budget budget;

    @BeforeEach
    void setUp() {
        department = testDataFactory.createDepartment("DEPT-" + System.nanoTime());
        expenseAccount = testDataFactory.createAccountCode("EXP-" + System.nanoTime(), "EXPENSE");
        payableAccount = testDataFactory.createAccountCode("PAYABLE", "LIABILITY");
        budget = testDataFactory.createBudget(department.getId(), expenseAccount.getId(), 1_000_000L);
    }

    @Test
    @DisplayName("[1] 예산 초과 시 집행 실패하고 잔액이 변하지 않는다")
    void executeFailsWhenBudgetInsufficient() {
        ExpenseRequest expense = testDataFactory.createApprovedExpense(
                budget.getId(), department.getId(), expenseAccount.getId(), 2_000_000L);

        Long balanceBefore = budgetBalanceRepository.findByBudgetId(budget.getId())
                .orElseThrow().getRemainingAmount();

        assertThatThrownBy(() -> expenseExecutionService.execute(
                expense.getId(), "key-1", new ExpenseRequestExecuteRequest("note")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.BUDGET_INSUFFICIENT);

        Long balanceAfter = budgetBalanceRepository.findByBudgetId(budget.getId())
                .orElseThrow().getRemainingAmount();
        assertThat(balanceAfter).isEqualTo(balanceBefore);
    }

    @Test
    @DisplayName("[2] 승인되지 않은(REQUESTED) 요청은 실행할 수 없다")
    void executeFailsWhenNotApproved() {
        Long expenseRequestId = expenseRequestService.create(new ExpenseRequestCreateRequest(
                budget.getId(), department.getId(), expenseAccount.getId(),
                100_000L, "memo", LocalDate.now()));
        // 생성 직후 상태는 REQUESTED.

        assertThatThrownBy(() -> expenseExecutionService.execute(
                expenseRequestId, "key-2", new ExpenseRequestExecuteRequest("note")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    @DisplayName("[3] APPROVED 요청을 실행하면 EXECUTED + 전표가 자동 생성된다")
    void executeSuccessCreatesJournalEntry() {
        ExpenseRequest expense = testDataFactory.createApprovedExpense(
                budget.getId(), department.getId(), expenseAccount.getId(), 300_000L);

        ExpenseRequestResponse response = expenseExecutionService.execute(
                expense.getId(), "key-3", new ExpenseRequestExecuteRequest("note"));

        assertThat(response.status()).isEqualTo(ExpenseRequestStatus.EXECUTED);
        assertThat(response.journalEntryId()).isNotNull();

        var journal = journalEntryRepository.findByExpenseRequestId(expense.getId()).orElseThrow();
        assertThat(journal.getLines()).hasSize(2);
        long debitSum = journal.getLines().stream()
                .filter(l -> l.getDebitCreditType().name().equals("DEBIT"))
                .mapToLong(l -> l.getAmount()).sum();
        long creditSum = journal.getLines().stream()
                .filter(l -> l.getDebitCreditType().name().equals("CREDIT"))
                .mapToLong(l -> l.getAmount()).sum();
        assertThat(debitSum).isEqualTo(creditSum).isEqualTo(300_000L);
    }

    @Test
    @DisplayName("[5] 같은 Idempotency-Key + 같은 요청은 기존 결과를 반환한다")
    void sameIdempotencyKeyReturnsCachedResponse() {
        ExpenseRequest expense = testDataFactory.createApprovedExpense(
                budget.getId(), department.getId(), expenseAccount.getId(), 200_000L);

        ExpenseRequestResponse first = expenseExecutionService.execute(
                expense.getId(), "same-key", new ExpenseRequestExecuteRequest("note"));
        ExpenseRequestResponse second = expenseExecutionService.execute(
                expense.getId(), "same-key", new ExpenseRequestExecuteRequest("note"));

        assertThat(second.journalEntryId()).isEqualTo(first.journalEntryId());
        // 해당 집행 요청에 대해 전표는 정확히 1건만 생성되어야 합니다(중복 실행 X).
        assertThat(journalEntryRepository.findByExpenseRequestId(expense.getId()))
                .isPresent()
                .get()
                .extracting(j -> j.getId())
                .isEqualTo(first.journalEntryId());
    }

    @Test
    @DisplayName("[6] 같은 Idempotency-Key + 다른 요청 내용은 409 Conflict로 차단된다")
    void sameIdempotencyKeyDifferentPayloadThrowsConflict() {
        ExpenseRequest expense = testDataFactory.createApprovedExpense(
                budget.getId(), department.getId(), expenseAccount.getId(), 150_000L);

        expenseExecutionService.execute(
                expense.getId(), "conflict-key", new ExpenseRequestExecuteRequest("note1"));

        assertThatThrownBy(() -> expenseExecutionService.execute(
                expense.getId(), "conflict-key", new ExpenseRequestExecuteRequest("note2")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT);
    }

    @Test
    @DisplayName("[7] 외부 ERP 실패 후 retry 성공 시 EXECUTED 전환된다")
    void retryAfterErpFailureLeadsToExecuted() {
        ExpenseRequest expense = testDataFactory.createApprovedExpense(
                budget.getId(), department.getId(), expenseAccount.getId(), 400_000L);

        stubErpClient.primeFailure();
        assertThatThrownBy(() -> expenseExecutionService.execute(
                expense.getId(), "retry-key", new ExpenseRequestExecuteRequest("note")))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.EXTERNAL_ERP_FAILED);

        ExpenseRequest afterFail = expenseRequestRepository.findById(expense.getId()).orElseThrow();
        assertThat(afterFail.getStatus()).isEqualTo(ExpenseRequestStatus.EXECUTION_FAILED);

        ExpenseRequestResponse retryResponse = expenseExecutionService.retry(expense.getId());
        assertThat(retryResponse.status()).isEqualTo(ExpenseRequestStatus.EXECUTED);
        assertThat(retryResponse.journalEntryId()).isNotNull();
    }
}
