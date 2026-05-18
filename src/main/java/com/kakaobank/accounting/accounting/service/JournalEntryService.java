package com.kakaobank.accounting.accounting.service;

import com.kakaobank.accounting.accounting.domain.JournalEntry;
import com.kakaobank.accounting.accounting.domain.JournalLine;
import com.kakaobank.accounting.accounting.repository.JournalEntryRepository;
import com.kakaobank.accounting.budget.domain.AccountCode;
import com.kakaobank.accounting.budget.repository.AccountCodeRepository;
import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 회계전표 생성/조회 서비스.
 *
 * 정책:
 *   - 비용 집행 1건당 1전표 생성 (단순 비용 시나리오).
 *   - 차변: 비용 계정과목 (집행 요청에 지정된 계정과목)
 *   - 대변: 미지급금 / 현금 등 결제수단 계정 (본 과제에서는 단일 PAYABLE 계정으로 고정)
 *   - 차변 합계 == 대변 합계 검증 후 저장.
 *
 * 트랜잭션 경계:
 *   - 본 메서드는 호출자의 트랜잭션 안에서 호출되도록 propagation을 기본값(REQUIRED)으로 둡니다.
 *   - 집행 상태가 EXECUTED로 바뀌는 트랜잭션과 전표 저장이 같은 묶음이어야
 *     "집행 EXECUTED인데 전표가 없는" 정합성 깨짐이 발생하지 않습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalEntryService {

    /**
     * 대변(결제수단) 기본 계정 코드.
     *
     * 실제 시스템이라면 결제수단/지급조건에 따라 분기가 필요하지만,
     * 과제 범위에서는 단일 PAYABLE 계정으로 고정합니다.
     */
    private static final String DEFAULT_CREDIT_ACCOUNT_CODE = "PAYABLE";

    private final JournalEntryRepository journalEntryRepository;
    private final AccountCodeRepository accountCodeRepository;

    @Transactional
    public JournalEntry createForExpense(ExpenseRequest expenseRequest) {
        log.info("JOURNAL_CREATE_START expenseRequestId={} amount={}",
                expenseRequest.getId(), expenseRequest.getAmount());

        JournalEntry entry = JournalEntry.create(expenseRequest.getId(), expenseRequest.getTargetDate());

        // 차변: 집행 대상 비용 계정
        entry.addLine(JournalLine.debit(expenseRequest.getAccountCodeId(), expenseRequest.getAmount()));

        // 대변: 기본 결제수단 계정 (PAYABLE)
        AccountCode creditAccount = accountCodeRepository.findByCode(DEFAULT_CREDIT_ACCOUNT_CODE)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_CODE_NOT_FOUND,
                        "기본 대변 계정(" + DEFAULT_CREDIT_ACCOUNT_CODE + ")이 존재하지 않습니다."));
        entry.addLine(JournalLine.credit(creditAccount.getId(), expenseRequest.getAmount()));

        // 저장 직전에 차/대변 합계 검증. 위반 시 예외가 던져져 트랜잭션 전체가 롤백됩니다.
        entry.validateBalanced();

        JournalEntry saved = journalEntryRepository.save(entry);
        log.info("JOURNAL_CREATE_OK journalEntryId={} expenseRequestId={}",
                saved.getId(), expenseRequest.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> findByTargetDate(LocalDate targetDate) {
        return journalEntryRepository.findByTargetDate(targetDate);
    }

    @Transactional(readOnly = true)
    public Optional<JournalEntry> findByExpenseRequestId(Long expenseRequestId) {
        return journalEntryRepository.findByExpenseRequestId(expenseRequestId);
    }
}
