package com.kakaobank.accounting.budget.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 예산 엔티티.
 *
 * 부서 + 계정과목 + 회계연도 단위로 "이 만큼 사용 가능"이라는 한도를 정의합니다.
 * 실제 차감되는 잔액은 BudgetBalance에 분리해 두었습니다.
 *
 * 분리 이유:
 *   - 한도(allocatedAmount)는 자주 변경되지 않는 마스터 데이터입니다.
 *   - 잔액(remainingAmount)은 집행마다 변경되며 동시성 충돌이 발생할 수 있습니다.
 *   - 마스터/트랜잭션 데이터를 분리해 잠금 범위를 좁히고, 마스터 데이터에 부하가 가지 않도록 합니다.
 *
 * 금액은 Long(원 단위)로 저장합니다. KRW는 소수점이 없고, 정수 연산이 BigDecimal보다 단순·빠릅니다.
 */
@Entity
@Table(name = "budget",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_budget_dept_account_year",
                columnNames = {"department_id", "account_code_id", "fiscal_year"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "account_code_id", nullable = false)
    private Long accountCodeId;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    /**
     * 배정 금액. 변경 가능성은 있지만 매우 드물게 일어나므로
     * 본 과제에서는 변경 API를 따로 두지 않습니다.
     */
    @Column(name = "allocated_amount", nullable = false)
    private Long allocatedAmount;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDate validTo;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static Budget create(Long departmentId,
                                Long accountCodeId,
                                Integer fiscalYear,
                                Long allocatedAmount,
                                LocalDate validFrom,
                                LocalDate validTo) {
        return Budget.builder()
                .departmentId(departmentId)
                .accountCodeId(accountCodeId)
                .fiscalYear(fiscalYear)
                .allocatedAmount(allocatedAmount)
                .validFrom(validFrom)
                .validTo(validTo)
                .build();
    }
}
