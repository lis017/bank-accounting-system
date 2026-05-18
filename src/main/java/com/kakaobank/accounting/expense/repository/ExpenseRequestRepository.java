package com.kakaobank.accounting.expense.repository;

import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;

public interface ExpenseRequestRepository extends JpaRepository<ExpenseRequest, Long> {

    /**
     * 일마감 배치 전용 조회.
     *
     * Spring Batch의 RepositoryItemReader가 이 메서드를 호출합니다.
     * RepositoryItemReader는 마지막 인자로 Pageable을 주입하므로, 메서드 시그니처에 Pageable을 포함해야 합니다.
     * 반환 타입도 Page여야 페이지네이션과 카운트를 같이 가져갈 수 있습니다.
     */
    Page<ExpenseRequest> findByTargetDateAndStatus(LocalDate targetDate, ExpenseRequestStatus status, Pageable pageable);
}
