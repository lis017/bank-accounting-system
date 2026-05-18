package com.kakaobank.accounting.expense.repository;

import com.kakaobank.accounting.expense.domain.ExpenseExecutionHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExpenseExecutionHistoryRepository extends JpaRepository<ExpenseExecutionHistory, Long> {
}
