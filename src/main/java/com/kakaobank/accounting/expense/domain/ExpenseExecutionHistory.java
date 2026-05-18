package com.kakaobank.accounting.expense.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 집행 실행 이력 엔티티.
 *
 * 책임:
 *   - 한 ExpenseRequest가 EXECUTED 또는 EXECUTION_FAILED로 갈 때마다 1건씩 기록됩니다.
 *   - 재처리(retry)도 별도의 이력으로 남아, "언제 어떤 결과로 시도했는지" 감사 추적이 가능합니다.
 *
 * 면접 포인트:
 *   - 상태 컬럼만으로는 retry 결과를 누적해서 추적하기 어렵습니다.
 *   - 이력 테이블을 별도로 두면 운영에서 장애 분석이 훨씬 쉬워집니다.
 */
@Entity
@Table(name = "expense_execution_history",
        indexes = @Index(name = "idx_eeh_expense_request", columnList = "expense_request_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExpenseExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_request_id", nullable = false)
    private Long expenseRequestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_status", nullable = false, length = 32)
    private ExpenseRequestStatus resultStatus;

    @Column(name = "external_reference_id", length = 64)
    private String externalReferenceId;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "executed_at", nullable = false)
    private LocalDateTime executedAt;

    @PrePersist
    void onCreate() {
        this.executedAt = LocalDateTime.now();
    }

    public static ExpenseExecutionHistory success(Long expenseRequestId, String externalReferenceId) {
        return ExpenseExecutionHistory.builder()
                .expenseRequestId(expenseRequestId)
                .resultStatus(ExpenseRequestStatus.EXECUTED)
                .externalReferenceId(externalReferenceId)
                .build();
    }

    public static ExpenseExecutionHistory failure(Long expenseRequestId, String failureReason) {
        return ExpenseExecutionHistory.builder()
                .expenseRequestId(expenseRequestId)
                .resultStatus(ExpenseRequestStatus.EXECUTION_FAILED)
                .failureReason(failureReason)
                .build();
    }
}
