package com.kakaobank.accounting.batch;

import com.kakaobank.accounting.batch.daily.DailyClosingJobConfig;
import com.kakaobank.accounting.budget.domain.AccountCode;
import com.kakaobank.accounting.budget.domain.Budget;
import com.kakaobank.accounting.budget.domain.Department;
import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;
import com.kakaobank.accounting.expense.dto.ExpenseRequestExecuteRequest;
import com.kakaobank.accounting.expense.repository.ExpenseRequestRepository;
import com.kakaobank.accounting.expense.service.ExpenseExecutionService;
import com.kakaobank.accounting.support.StubErpClient;
import com.kakaobank.accounting.support.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * [8] dailyClosingJob 성공 시 EXECUTED 건이 CLOSED로 처리되는지 검증.
 */
@SpringBootTest
@SpringBatchTest
@Import(StubErpClient.TestConfig.class)
class DailyClosingJobTest {

    @Autowired private JobLauncherTestUtils jobLauncherTestUtils;
    @Autowired private TestDataFactory testDataFactory;
    @Autowired private ExpenseExecutionService expenseExecutionService;
    @Autowired private ExpenseRequestRepository expenseRequestRepository;

    private Department department;
    private AccountCode expenseAccount;
    private Budget budget;

    @BeforeEach
    void setUp() {
        department = testDataFactory.createDepartment("BATCH-DEPT-" + System.nanoTime());
        expenseAccount = testDataFactory.createAccountCode("BATCH-EXP-" + System.nanoTime(), "EXPENSE");
        testDataFactory.createAccountCode("PAYABLE", "LIABILITY");
        budget = testDataFactory.createBudget(department.getId(), expenseAccount.getId(), 10_000_000L);
    }

    @Test
    @DisplayName("[8] dailyClosingJob 실행 시 EXECUTED 건이 CLOSED 로 전이된다")
    void dailyClosingJobClosesExecutedRequests() throws Exception {
        ExpenseRequest approved = testDataFactory.createApprovedExpense(
                budget.getId(), department.getId(), expenseAccount.getId(), 100_000L);
        expenseExecutionService.execute(
                approved.getId(), "batch-key", new ExpenseRequestExecuteRequest("note"));

        // 사전 조건 검증.
        ExpenseRequest beforeBatch = expenseRequestRepository.findById(approved.getId()).orElseThrow();
        assertThat(beforeBatch.getStatus()).isEqualTo(ExpenseRequestStatus.EXECUTED);

        JobParameters params = new JobParametersBuilder()
                .addString("targetDate", LocalDate.now().toString())
                .addLong("triggeredAt", System.currentTimeMillis())
                .toJobParameters();
        JobExecution execution = jobLauncherTestUtils.launchJob(params);

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        ExpenseRequest afterBatch = expenseRequestRepository.findById(approved.getId()).orElseThrow();
        assertThat(afterBatch.getStatus()).isEqualTo(ExpenseRequestStatus.CLOSED);
    }
}
