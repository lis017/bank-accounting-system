package com.kakaobank.accounting.integration.erp;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 외부 ERP 시스템을 흉내내는 모의 컨트롤러.
 *
 * 동작 규칙:
 *   - 금액이 999999999L 이면 강제로 실패를 반환합니다 (테스트/데모용).
 *   - 그 외에는 성공으로 처리합니다.
 *
 * 면접 포인트:
 *   - 외부 API의 실패 시나리오를 통합 테스트로 재현하려면 mock 엔드포인트가 매우 유용합니다.
 *   - 단, 운영 빌드에 노출되지 않도록 별도 프로파일/패키지로 분리하는 것이 안전합니다.
 *   - 본 과제 버전에서는 단순화를 위해 노출했지만, 실무에서는 spring profile로 가드합니다.
 */
@Slf4j
@RestController
@RequestMapping("/mock/erp")
public class MockErpController {

    private static final long FORCE_FAILURE_AMOUNT = 999_999_999L;

    @PostMapping("/payments")
    public ResponseEntity<ErpPaymentResponse> requestPayment(@Valid @RequestBody ErpPaymentRequest request) {
        log.info("MOCK_ERP_RECEIVED expenseRequestId={} amount={}",
                request.expenseRequestId(), request.amount());
        if (request.amount() == FORCE_FAILURE_AMOUNT) {
            return ResponseEntity.ok(ErpPaymentResponse.failure("FORCED_FAILURE_FOR_TEST"));
        }
        String reference = "ERP-" + UUID.randomUUID();
        return ResponseEntity.ok(ErpPaymentResponse.success(reference));
    }
}
