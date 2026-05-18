package com.kakaobank.accounting.batch.daily;

import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;
import com.kakaobank.accounting.expense.repository.ExpenseRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * 일마감 Writer.
 *
 * Processor에서 검증 통과한 ExpenseRequest 들을 받아 일괄적으로 CLOSED 상태로 전이합니다.
 *
 * 면접 포인트:
 *   - Writer는 Chunk 단위 트랜잭션으로 호출됩니다. Chunk 안에서 1건만 예외가 나도
 *     해당 Chunk 전체가 롤백되므로, "검증 실패 건은 Processor에서 미리 걸러" Writer엔 안전한 건만 옵니다.
 *   - JPA save는 dirty checking으로 update 처리됩니다.
 */
@Slf4j
@RequiredArgsConstructor
public class DailyClosingWriter implements ItemWriter<ExpenseRequest> {

    private final ExpenseRequestRepository expenseRequestRepository;

    @Override
    public void write(Chunk<? extends ExpenseRequest> chunk) {
        for (ExpenseRequest item : chunk.getItems()) {
            ExpenseRequestStatus before = item.getStatus();
            item.changeStatus(ExpenseRequestStatus.CLOSED);
            // 명시적으로 save를 호출하지 않아도 영속 상태이므로 dirty checking으로 업데이트되지만,
            // 가독성을 위해 save를 호출합니다.
            expenseRequestRepository.save(item);
            log.info("DAILY_CLOSING_CLOSED expenseRequestId={} before={} after={}",
                    item.getId(), before, item.getStatus());
        }
    }
}
