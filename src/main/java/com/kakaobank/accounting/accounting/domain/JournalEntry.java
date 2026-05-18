package com.kakaobank.accounting.accounting.domain;

import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 회계전표 엔티티.
 *
 * 1건의 집행에 대해 1건의 전표가 생성됩니다.
 * 한 전표는 여러 라인(JournalLine)으로 구성되며, 반드시 차변 합계 == 대변 합계를 만족해야 합니다.
 *
 * 도메인 불변식(절대 깨지면 안 되는 규칙):
 *   1. 차변 합계 == 대변 합계 (1원도 오차 허용 X)
 *   2. 한 전표 안에는 최소 1개 이상의 차변과 1개 이상의 대변 라인이 존재
 *
 * 이 검증을 Service나 Controller가 아니라 엔티티 안에서 수행하는 이유는
 * "어떤 경로로 전표가 만들어지든 같은 규칙이 강제되어야" 하기 때문입니다.
 */
@Entity
@Table(name = "journal_entry",
        indexes = {
                @Index(name = "idx_journal_entry_target_date", columnList = "target_date"),
                @Index(name = "idx_journal_entry_expense_request", columnList = "expense_request_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "expense_request_id", nullable = false)
    private Long expenseRequestId;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private JournalEntryStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<JournalLine> lines = new ArrayList<>();

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = JournalEntryStatus.POSTED;
    }

    public static JournalEntry create(Long expenseRequestId, LocalDate targetDate) {
        return JournalEntry.builder()
                .expenseRequestId(expenseRequestId)
                .targetDate(targetDate)
                .status(JournalEntryStatus.POSTED)
                .lines(new ArrayList<>())
                .build();
    }

    /**
     * 라인을 추가합니다. 양방향 관계의 일관성을 유지하기 위해 부모에서 자식의 참조까지 세팅합니다.
     */
    public void addLine(JournalLine line) {
        line.setJournalEntry(this);
        this.lines.add(line);
    }

    /**
     * 차변/대변 합계가 같은지 검증합니다.
     *
     * 트랜잭션 안에서 전표 저장 직전에 호출되며, 한 번이라도 불일치하면 예외를 던져 트랜잭션을 롤백합니다.
     * 이를 통해 "불균형 전표"가 DB에 절대로 들어가지 않도록 보장합니다.
     */
    public void validateBalanced() {
        long debitSum = 0L;
        long creditSum = 0L;
        boolean hasDebit = false;
        boolean hasCredit = false;
        for (JournalLine line : lines) {
            if (line.getDebitCreditType() == DebitCreditType.DEBIT) {
                debitSum += line.getAmount();
                hasDebit = true;
            } else {
                creditSum += line.getAmount();
                hasCredit = true;
            }
        }
        if (!hasDebit || !hasCredit) {
            throw new BusinessException(ErrorCode.JOURNAL_NOT_BALANCED,
                    "차변과 대변 라인이 모두 1건 이상 존재해야 합니다.");
        }
        if (debitSum != creditSum) {
            throw new BusinessException(ErrorCode.JOURNAL_NOT_BALANCED,
                    "차변 합계=" + debitSum + ", 대변 합계=" + creditSum);
        }
    }
}
