package com.kakaobank.accounting.accounting.repository;

import com.kakaobank.accounting.accounting.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @Query("select distinct je from JournalEntry je left join fetch je.lines where je.targetDate = :targetDate")
    List<JournalEntry> findByTargetDate(@Param("targetDate") LocalDate targetDate);

    /**
     * 라인까지 한 번에 가져오는 fetch join.
     *
     * 일마감 Processor 등 트랜잭션이 짧게 끝난 뒤 라인을 봐야 하는 경로에서 사용합니다.
     * LAZY 기본값을 유지하면서, 필요한 곳에서만 명시적으로 EAGER 로딩을 한다는 패턴입니다.
     */
    @Query("select je from JournalEntry je left join fetch je.lines where je.expenseRequestId = :expenseRequestId")
    Optional<JournalEntry> findByExpenseRequestId(@Param("expenseRequestId") Long expenseRequestId);
}
