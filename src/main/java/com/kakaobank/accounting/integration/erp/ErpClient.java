package com.kakaobank.accounting.integration.erp;

/**
 * 외부 ERP 시스템 호출 추상화.
 *
 * 실제 운영에서는 HTTP/메시지큐 등 외부 통신을 수행하지만,
 * 본 과제에서는 테스트 가능성과 격리를 위해 인터페이스로 추상화합니다.
 *
 * 면접 포인트:
 *   - 외부 API 호출을 인터페이스로 추상화하면 테스트에서 손쉽게 실패 시나리오를 주입할 수 있습니다.
 *   - 또한 ERP가 교체되어도 도메인 코드가 영향받지 않습니다(의존성 역전).
 */
public interface ErpClient {

    ErpPaymentResponse requestPayment(ErpPaymentRequest request);
}
