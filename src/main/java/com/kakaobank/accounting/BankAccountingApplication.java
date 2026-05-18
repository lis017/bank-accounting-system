package com.kakaobank.accounting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 카카오뱅크 금융회계 백엔드 과제 대비용 메인 클래스.
 *
 * 이 프로젝트의 핵심 흐름은 다음과 같습니다.
 *   예산 등록 → 집행 요청 생성 → 승인 → 집행 실행 → 회계전표 자동 생성 → 일마감 배치 → 실패 재처리
 *
 * 단일 애플리케이션으로 구현했으며, 이는 데이터 정합성(특히 차변/대변 균형, 멱등성, 상태전이)이
 * 분산 처리보다 우선이라는 판단 때문입니다. README의 면접 예상 질문에 상세히 정리했습니다.
 */
@SpringBootApplication
public class BankAccountingApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankAccountingApplication.class, args);
    }
}
