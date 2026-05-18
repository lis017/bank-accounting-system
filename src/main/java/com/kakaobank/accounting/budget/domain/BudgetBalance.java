package com.kakaobank.accounting.budget.domain;

import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

/**
 * 예산 잔액 엔티티.
 *
 * Budget과 1:1로 분리한 이유는 Budget 클래스의 주석에 정리되어 있습니다.
 *
 * 동시성 제어:
 *   - @Version을 사용한 낙관적 락을 채택했습니다.
 *   - 예산 차감은 짧고 빈번한 트랜잭션이며, 충돌 발생 시 클라이언트가 재시도하면 자연스럽게 해결됩니다.
 *   - 비관적 락(SELECT FOR UPDATE)은 데드락/락 대기 시간이 길어질 위험이 있고,
 *     MySQL → Oracle 전환 시 동작 차이가 더 큽니다. 낙관적 락이 더 이식성이 좋습니다.
 *
 * 도메인 규칙:
 *   - remainingAmount는 절대 음수가 될 수 없습니다.
 *   - 차감 시 remainingAmount < amount 이면 BUDGET_INSUFFICIENT를 던집니다.
 *
 * 면접 포인트: 잔액 검증을 Service가 아니라 엔티티에서 수행하는 이유는,
 * "잔액이 음수일 수 없다"라는 규칙은 도메인 자체의 불변식이기 때문입니다.
 * 어디서 호출되든 같은 검증이 적용되도록 엔티티 안에 둡니다.
 */
@Entity
@Table(name = "budget_balance",
        uniqueConstraints = @UniqueConstraint(name = "uq_budget_balance_budget", columnNames = "budget_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BudgetBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "budget_id", nullable = false)
    private Long budgetId;

    @Column(name = "remaining_amount", nullable = false)
    private Long remainingAmount;

    /** 낙관적 락 버전. JPA가 update 시 where version = ? 조건을 자동으로 붙입니다. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public static BudgetBalance initialize(Long budgetId, Long initialAmount) {
        return BudgetBalance.builder()
                .budgetId(budgetId)
                .remainingAmount(initialAmount)
                .build();
    }

    /**
     * 잔액에서 amount만큼 차감합니다.
     *
     * 트랜잭션 경계는 호출하는 Service의 @Transactional이 관리합니다.
     * 본 메서드는 호출 시점의 잔액 상태에서 도메인 규칙만 확인합니다.
     */
    public void decrease(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "차감 금액은 0보다 커야 합니다.");
        }
        if (this.remainingAmount < amount) {
            throw new BusinessException(ErrorCode.BUDGET_INSUFFICIENT,
                    "예산이 부족합니다. 잔액=" + this.remainingAmount + ", 요청=" + amount);
        }
        this.remainingAmount -= amount;
    }

    /**
     * 집행 취소/롤백 시 잔액을 다시 채워 넣습니다.
     */
    public void restore(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "복원 금액은 0보다 커야 합니다.");
        }
        this.remainingAmount += amount;
    }
}
