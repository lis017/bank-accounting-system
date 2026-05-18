package com.kakaobank.accounting.support;

import com.kakaobank.accounting.integration.erp.ErpClient;
import com.kakaobank.accounting.integration.erp.ErpPaymentRequest;
import com.kakaobank.accounting.integration.erp.ErpPaymentResponse;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 테스트에서 외부 ERP 동작을 마음대로 제어하기 위한 스텁.
 *
 * - failNextCall(true)로 다음 호출 1회를 실패시킬 수 있습니다.
 * - 그 외에는 항상 성공.
 */
public class StubErpClient implements ErpClient {

    private final AtomicBoolean failNextCall = new AtomicBoolean(false);

    public void primeFailure() {
        failNextCall.set(true);
    }

    @Override
    public ErpPaymentResponse requestPayment(ErpPaymentRequest request) {
        if (failNextCall.getAndSet(false)) {
            return ErpPaymentResponse.failure("STUB_FAIL");
        }
        return ErpPaymentResponse.success("ERP-STUB-" + UUID.randomUUID());
    }

    @TestConfiguration
    public static class TestConfig {
        @Bean
        @Primary
        public StubErpClient stubErpClient() {
            return new StubErpClient();
        }
    }
}
