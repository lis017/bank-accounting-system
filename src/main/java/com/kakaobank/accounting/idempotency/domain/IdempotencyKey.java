package com.kakaobank.accounting.idempotency.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 멱등성 키 엔티티.
 *
 * 책임:
 *   - 클라이언트가 Header(Idempotency-Key)로 보낸 키를 DB에 저장하여 중복 실행을 방지합니다.
 *   - 같은 키 + 같은 요청 내용이면 기존 응답을 재사용합니다.
 *   - 같은 키 + 다른 요청 내용이면 409 Conflict로 차단합니다.
 *
 * 키 자체에 unique 제약을 걸고, requestHash로 요청 내용 동일성을 비교합니다.
 *
 * 면접 포인트:
 *   - 멱등성 키가 없거나 캐시(Redis)로만 처리하면 장애 시 중복 결제/전표가 발생할 수 있습니다.
 *   - DB에 unique 제약으로 저장하면 외부 캐시 장애에도 데이터 정합성이 유지됩니다.
 */
@Entity
@Table(name = "idempotency_key",
        uniqueConstraints = @UniqueConstraint(name = "uq_idempotency_key", columnNames = "idempotency_key"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class IdempotencyKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    /** 요청 내용 동일성을 비교하기 위한 SHA-256 hex 해시. */
    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private IdempotencyStatus status;

    /** 응답 본문 스냅샷 (JSON 문자열). PROCESSING 중에는 null. */
    @Lob
    @Column(name = "response_snapshot")
    private String responseSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public static IdempotencyKey start(String idempotencyKey, String requestHash) {
        return IdempotencyKey.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .status(IdempotencyStatus.PROCESSING)
                .build();
    }

    public void markCompleted(String responseSnapshot) {
        this.status = IdempotencyStatus.COMPLETED;
        this.responseSnapshot = responseSnapshot;
    }

    public void markFailed(String responseSnapshot) {
        this.status = IdempotencyStatus.FAILED;
        this.responseSnapshot = responseSnapshot;
    }
}
