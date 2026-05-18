package com.kakaobank.accounting.budget.controller;

import com.kakaobank.accounting.budget.dto.AccountCodeRequest;
import com.kakaobank.accounting.budget.dto.BudgetBalanceResponse;
import com.kakaobank.accounting.budget.dto.BudgetRequest;
import com.kakaobank.accounting.budget.dto.DepartmentRequest;
import com.kakaobank.accounting.budget.service.BudgetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * 예산/부서/계정과목 관련 REST API.
 *
 * REST 설계 원칙:
 *   - 식별자는 PathVariable, 검색 조건은 RequestParam, 생성 데이터는 RequestBody로 분리합니다.
 *   - 생성 응답은 201 + Location 헤더로 명확히 표현합니다.
 *
 * 컨트롤러는 얇게 유지하고, 트랜잭션/도메인 로직은 Service에 둡니다.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BudgetController {

    private final BudgetService budgetService;

    @PostMapping("/departments")
    public ResponseEntity<Void> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        Long id = budgetService.createDepartment(request);
        return ResponseEntity.created(URI.create("/api/v1/departments/" + id)).build();
    }

    @PostMapping("/account-codes")
    public ResponseEntity<Void> createAccountCode(@Valid @RequestBody AccountCodeRequest request) {
        Long id = budgetService.createAccountCode(request);
        return ResponseEntity.created(URI.create("/api/v1/account-codes/" + id)).build();
    }

    @PostMapping("/budgets")
    public ResponseEntity<Void> createBudget(@Valid @RequestBody BudgetRequest request) {
        Long id = budgetService.createBudget(request);
        return ResponseEntity.created(URI.create("/api/v1/budgets/" + id)).build();
    }

    @GetMapping("/budgets/{budgetId}/balance")
    public ResponseEntity<BudgetBalanceResponse> getBalance(@PathVariable Long budgetId) {
        return ResponseEntity.status(HttpStatus.OK).body(budgetService.getBalance(budgetId));
    }
}
