package com.kakaobank.accounting.budget.repository;

import com.kakaobank.accounting.budget.domain.AccountCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountCodeRepository extends JpaRepository<AccountCode, Long> {
    Optional<AccountCode> findByCode(String code);
}
