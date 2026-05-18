package com.kakaobank.accounting.integration.erp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 같은 애플리케이션 내부에 띄워둔 Mock ERP 컨트롤러로 HTTP 호출하는 클라이언트.
 *
 * 책임:
 *   - 외부 호출 실패(IOException 등)는 ErpPaymentResponse.failure 로 변환합니다.
 *   - 호출 시간이 트랜잭션 외부에 있도록 호출자가 트랜잭션을 분리해 호출해야 합니다.
 *     (ExpenseExecutionService.execute 참고)
 *
 * 면접 포인트:
 *   - RestTemplate에 connectTimeout/readTimeout을 명시적으로 설정합니다.
 *   - 무한 대기로 인한 트랜잭션 장기 점유, DB 커넥션 풀 고갈 방지를 위해 필수입니다.
 */
@Slf4j
@Component
@Profile("!test")
public class MockErpClient implements ErpClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public MockErpClient(RestTemplateBuilder builder,
                         @Value("${app.erp.base-url:http://localhost:8080}") String baseUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofSeconds(2))
                .setReadTimeout(Duration.ofSeconds(3))
                .build();
        this.baseUrl = baseUrl;
    }

    @Override
    public ErpPaymentResponse requestPayment(ErpPaymentRequest request) {
        String url = baseUrl + "/mock/erp/payments";
        log.info("ERP_CALL_START expenseRequestId={} amount={}", request.expenseRequestId(), request.amount());
        try {
            ResponseEntity<ErpPaymentResponse> response = restTemplate.postForEntity(url, request, ErpPaymentResponse.class);
            ErpPaymentResponse body = response.getBody();
            if (body == null) {
                log.warn("ERP_CALL_FAIL expenseRequestId={} reason=emptyBody", request.expenseRequestId());
                return ErpPaymentResponse.failure("ERP 응답 본문이 비어있습니다.");
            }
            if (body.success()) {
                log.info("ERP_CALL_OK expenseRequestId={} ref={}", request.expenseRequestId(), body.externalReferenceId());
            } else {
                log.warn("ERP_CALL_FAIL expenseRequestId={} reason={}", request.expenseRequestId(), body.failureReason());
            }
            return body;
        } catch (RestClientException ex) {
            log.warn("ERP_CALL_FAIL expenseRequestId={} reason={}", request.expenseRequestId(), ex.getMessage());
            return ErpPaymentResponse.failure("ERP 호출 실패: " + ex.getMessage());
        }
    }
}
