package com.kakaobank.accounting.budget.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 계정과목 엔티티.
 *
 * 회계에서 모든 거래는 "차변/대변에 어떤 계정과목으로 분개할 것인가"가 핵심입니다.
 * 본 과제에서는 예산 분류와 회계전표 라인의 분개 기준 양쪽에서 사용됩니다.
 *
 * accountType은 ASSET / LIABILITY / EXPENSE / REVENUE / EQUITY 등의 큰 분류이며,
 * 본 과제 범위에서는 EXPENSE / CASH / PAYABLE만 다뤄도 충분하므로 enum 없이 문자열로 둡니다.
 * 실제 운영에서는 별도 enum으로 강제하는 게 안전합니다.
 */
@Entity
@Table(name = "account_code",
        uniqueConstraints = @UniqueConstraint(name = "uq_account_code", columnNames = "code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class AccountCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "code_name", nullable = false, length = 64)
    private String codeName;

    @Column(name = "account_type", nullable = false, length = 16)
    private String accountType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public static AccountCode create(String code, String codeName, String accountType) {
        return AccountCode.builder()
                .code(code)
                .codeName(codeName)
                .accountType(accountType)
                .build();
    }
}
