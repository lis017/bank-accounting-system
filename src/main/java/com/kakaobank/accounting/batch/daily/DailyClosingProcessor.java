package com.kakaobank.accounting.batch.daily;

import com.kakaobank.accounting.accounting.domain.JournalEntry;
import com.kakaobank.accounting.accounting.repository.JournalEntryRepository;
import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.ItemProcessor;

/**
 * 일마감 Processor.
 *
 * 책임:
 *   - 입력으로 들어온 EXECUTED 상태의 ExpenseRequest 1건에 대해
 *     연결된 회계전표의 차/대변 합계 검증을 수행합니다.
 *   - 검증 통과 시 Writer로 ExpenseRequest를 전달하고,
 *     실패 시 null을 반환해 Writer에서 제외(skip)합니다.
 *
 * 면접 포인트:
 *   - 검증과 상태 전환을 같은 Processor에서 하지 않고, "검증은 Processor / 상태 전환은 Writer"로 분리했습니다.
 *   - Spring Batch에서 Processor는 read-only/순수 함수에 가깝게 두는 것이 가장 안전합니다.
 *   - 실패 카운트는 JobContext에 누적해 Job 종료 시 DailyClosingResult로 한 번에 저장합니다.
 */
@Slf4j
@RequiredArgsConstructor
public class DailyClosingProcessor implements ItemProcessor<ExpenseRequest, ExpenseRequest> {

    private final JournalEntryRepository journalEntryRepository;
    private final DailyClosingCounter counter;

    @Override
    public ExpenseRequest process(ExpenseRequest item) {
        counter.incrementTotal();
        JournalEntry journal = journalEntryRepository.findByExpenseRequestId(item.getId()).orElse(null);
        if (journal == null) {
            log.warn("DAILY_CLOSING_VALIDATION_FAIL expenseRequestId={} reason=no-journal", item.getId());
            counter.incrementFailure();
            return null;
        }
        try {
            journal.validateBalanced();
        } catch (Exception e) {
            log.warn("DAILY_CLOSING_VALIDATION_FAIL expenseRequestId={} reason={}", item.getId(), e.getMessage());
            counter.incrementFailure();
            return null;
        }
        counter.incrementSuccess();
        return item;
    }
}
