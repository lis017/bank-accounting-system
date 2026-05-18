package com.kakaobank.accounting.expense.controller;

import com.kakaobank.accounting.expense.dto.ExpenseRequestCreateRequest;
import com.kakaobank.accounting.expense.dto.ExpenseRequestExecuteRequest;
import com.kakaobank.accounting.expense.dto.ExpenseRequestResponse;
import com.kakaobank.accounting.expense.service.ExpenseExecutionService;
import com.kakaobank.accounting.expense.service.ExpenseRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * 집행 요청 REST API.
 *
 * REST 설계 원칙:
 *   - 식별자: PathVariable (expenseRequestId)
 *   - 멱등성/추적 값: Header (Idempotency-Key)
 *   - 생성 데이터: RequestBody
 * 이 원칙을 모든 엔드포인트에 일관되게 적용했습니다.
 */
@RestController
@RequestMapping("/api/v1/expense-requests")
@RequiredArgsConstructor
public class ExpenseRequestController {

    private final ExpenseRequestService expenseRequestService;
    private final ExpenseExecutionService expenseExecutionService;

    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody ExpenseRequestCreateRequest request) {
        Long id = expenseRequestService.create(request);
        return ResponseEntity.created(URI.create("/api/v1/expense-requests/" + id)).build();
    }

    @PostMapping("/{expenseRequestId}/approve")
    public ResponseEntity<Void> approve(@PathVariable Long expenseRequestId) {
        expenseRequestService.approve(expenseRequestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{expenseRequestId}/execute")
    public ResponseEntity<ExpenseRequestResponse> execute(
            @PathVariable Long expenseRequestId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ExpenseRequestExecuteRequest request) {
        ExpenseRequestResponse body = expenseExecutionService.execute(expenseRequestId, idempotencyKey, request);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{expenseRequestId}/retry")
    public ResponseEntity<ExpenseRequestResponse> retry(@PathVariable Long expenseRequestId) {
        return ResponseEntity.ok(expenseExecutionService.retry(expenseRequestId));
    }

    @GetMapping("/{expenseRequestId}")
    public ResponseEntity<ExpenseRequestResponse> findById(@PathVariable Long expenseRequestId) {
        return ResponseEntity.ok(expenseRequestService.findById(expenseRequestId));
    }
}
