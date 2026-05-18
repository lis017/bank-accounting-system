package com.kakaobank.accounting.budget.repository;

import com.kakaobank.accounting.budget.domain.Budget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
}
