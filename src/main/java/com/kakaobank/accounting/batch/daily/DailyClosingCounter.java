package com.kakaobank.accounting.batch.daily;

import lombok.Getter;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 일마감 배치 처리 건수 카운터.
 *
 * StepScope로 두어 Step 시작/종료마다 새 인스턴스가 만들어집니다.
 * Chunk가 여러 트랜잭션으로 나뉘어 실행되어도 같은 Step 내에서 누적 가능합니다.
 *
 * 면접 포인트:
 *   - Chunk마다 트랜잭션이 다르기 때문에 DB로 카운트를 누적하기보다,
 *     메모리에 누적 후 Step 종료 시 1회 저장하는 편이 성능/일관성 측면에서 유리합니다.
 */
@Getter
@Component
@StepScope
public class DailyClosingCounter {

    private final AtomicInteger total = new AtomicInteger();
    private final AtomicInteger success = new AtomicInteger();
    private final AtomicInteger failure = new AtomicInteger();

    public void incrementTotal() { total.incrementAndGet(); }
    public void incrementSuccess() { success.incrementAndGet(); }
    public void incrementFailure() { failure.incrementAndGet(); }

    public int getTotalCount() { return total.get(); }
    public int getSuccessCount() { return success.get(); }
    public int getFailureCount() { return failure.get(); }
}
