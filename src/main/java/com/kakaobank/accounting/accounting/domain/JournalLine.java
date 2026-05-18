package com.kakaobank.accounting.accounting.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 회계전표의 라인.
 *
 * 한 전표(JournalEntry) 안에 여러 라인이 들어가며,
 * 각 라인은 "어느 계정과목에 차변/대변으로 얼마"인지를 표현합니다.
 *
 * 부모-자식 관계:
 *   - JournalEntry @OneToMany(cascade = ALL, orphanRemoval = true) 로 묶여 있어,
 *     전표 저장 시 라인이 함께 저장됩니다.
 *   - 라인 단독으로 생성/수정되는 시나리오를 만들지 않습니다(전표 단위 트랜잭션 보장).
 */
@Entity
@Table(name = "journal_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(name = "account_code_id", nullable = false)
    private Long accountCodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "debit_credit_type", nullable = false, length = 8)
    private DebitCreditType debitCreditType;

    @Column(name = "amount", nullable = false)
    private Long amount;

    public static JournalLine debit(Long accountCodeId, Long amount) {
        return JournalLine.builder()
                .accountCodeId(accountCodeId)
                .debitCreditType(DebitCreditType.DEBIT)
                .amount(amount)
                .build();
    }

    public static JournalLine credit(Long accountCodeId, Long amount) {
        return JournalLine.builder()
                .accountCodeId(accountCodeId)
                .debitCreditType(DebitCreditType.CREDIT)
                .amount(amount)
                .build();
    }

    void setJournalEntry(JournalEntry journalEntry) {
        this.journalEntry = journalEntry;
    }
}
