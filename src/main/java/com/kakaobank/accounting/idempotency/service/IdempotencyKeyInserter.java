package com.kakaobank.accounting.idempotency.service;

import com.kakaobank.accounting.idempotency.domain.IdempotencyKey;
import com.kakaobank.accounting.idempotency.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 멱등성 키 등록 전용 헬퍼.
 *
 * 별도 빈/클래스로 분리한 이유:
 *   - REQUIRES_NEW 트랜잭션이 자체 호출(self-invocation)에서는 동작하지 않습니다.
 *   - unique constraint violation 발생 시 Hibernate 세션이 오염되어 같은 세션에서 후속 쿼리가 실패합니다.
 *   - 따라서 "insert만 수행하는 짧은 트랜잭션"을 외부 빈으로 분리해, 실패 시 깨끗한 상태로 복귀합니다.
 *
 * 면접 포인트:
 *   - "왜 같은 클래스의 @Transactional 메서드를 호출하는데 새 트랜잭션이 시작되지 않나요?" 라는
 *     단골 질문에 정답인 패턴입니다. CGLIB 프록시가 self-invocation을 가로채지 못하기 때문입니다.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyInserter {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyKey insert(IdempotencyKey newKey) {
        return idempotencyKeyRepository.saveAndFlush(newKey);
    }
}
