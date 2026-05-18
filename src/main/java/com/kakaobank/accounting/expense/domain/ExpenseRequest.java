package com.kakaobank.accounting.expense.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 집행 요청 엔티티.
 *
 * 도메인 관점:
 *   - 누가(부서) 어느 계정(계정과목)에 얼마(amount)를 어떤 명목(memo)으로 쓰겠다는 신청서입니다.
 *   - 신청 후 승인을 거쳐 실제 집행으로 이어지므로, 신청과 집행을 한 테이블에 묶지 않고
 *     상태(status)와 이력(ExpenseExecutionHistory)으로 분리합니다.
 *
 * 상태전이는 changeStatus 메서드를 통해서만 가능합니다.
 * setter를 노출하지 않는 이유는 외부에서 임의로 status를 바꾸는 사고를 방지하기 위해서입니다.
 */
@Entity
@Table(name = "expense_request",
        indexes = {
                @Index(name = "idx_expense_request_status", columnList = "status"),
                @Index(name = "idx_expense_request_target_date", columnList = "target_date")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class ExpenseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "budget_id", nullable = false)
    private Long budgetId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "account_code_id", nullable = false)
    private Long accountCodeId;

    /** 집행 금액(원). Long으로 저장합니다. 사유는 README 면접질문 참고. */
    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "memo", length = 200)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExpenseRequestStatus status;

    /**
     * 어느 회계 일자에 집행되는지.
     * 일마감 배치(dailyClosingJob)에서 이 컬럼을 기준으로 EXECUTED 건을 조회합니다.
     */
    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        if (this.status == null) this.status = ExpenseRequestStatus.DRAFT;
        if (this.retryCount == null) this.retryCount = 0;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static ExpenseRequest create(Long budgetId,
                                        Long departmentId,
                                        Long accountCodeId,
                                        Long amount,
                                        String memo,
                                        LocalDate targetDate) {
        return ExpenseRequest.builder()
                .budgetId(budgetId)
                .departmentId(departmentId)
                .accountCodeId(accountCodeId)
                .amount(amount)
                .memo(memo)
                .targetDate(targetDate)
                .status(ExpenseRequestStatus.REQUESTED)
                .retryCount(0)
                .build();
    }

    /**
     * 상태전이의 단일 진입점.
     *
     * 모든 상태 변경은 ExpenseRequestStatus.validateTransitionTo를 통과해야 하므로,
     * 잘못된 전이는 여기서 BusinessException으로 차단됩니다.
     */
    public void changeStatus(ExpenseRequestStatus next) {
        this.status.validateTransitionTo(next);
        this.status = next;
    }

    public void increaseRetryCount() {
        this.retryCount += 1;
    }
}
