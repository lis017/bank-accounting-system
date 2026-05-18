package com.kakaobank.accounting.budget.repository;

import com.kakaobank.accounting.budget.domain.BudgetBalance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BudgetBalanceRepository extends JpaRepository<BudgetBalance, Long> {
    Optional<BudgetBalance> findByBudgetId(Long budgetId);
}
