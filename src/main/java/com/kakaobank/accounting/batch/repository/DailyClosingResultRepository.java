package com.kakaobank.accounting.batch.repository;

import com.kakaobank.accounting.batch.domain.DailyClosingResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DailyClosingResultRepository extends JpaRepository<DailyClosingResult, Long> {
    List<DailyClosingResult> findByTargetDate(LocalDate targetDate);
}
