package com.kakaobank.accounting.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import com.kakaobank.accounting.common.util.LogMasker;
import com.kakaobank.accounting.idempotency.domain.IdempotencyKey;
import com.kakaobank.accounting.idempotency.domain.IdempotencyStatus;
import com.kakaobank.accounting.idempotency.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/**
 * 멱등성 키 처리 서비스.
 *
 * 처리 순서:
 *   1. 키가 없으면 IdempotencyKeyInserter(REQUIRES_NEW)로 새로 저장(PROCESSING).
 *      DB unique 제약이 동시 요청 중 단 1건만 성공시킵니다.
 *   2. 키가 이미 있으면 requestHash를 비교
 *      - 동일 + COMPLETED → 저장된 응답 그대로 반환 (재실행 X)
 *      - 동일 + PROCESSING → 처리 중이므로 409
 *      - 동일 + FAILED → 본 과제에서는 동일 실패 응답 반환 (재시도는 별도 retry API로)
 *      - 다름 → 409 (키 충돌)
 *
 * 면접 포인트:
 *   - acquire 자체는 @Transactional이 아닙니다. 외부 호출자의 트랜잭션과 분리되어,
 *     인서트 실패(unique 제약 위반) 시 새 세션으로 안전하게 재조회할 수 있습니다.
 *   - 만약 acquire가 @Transactional이고 그 안에서 unique 위반이 나면, 같은 세션에서
 *     후속 select가 AssertionFailure("null id ...")로 실패합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final IdempotencyKeyInserter idempotencyKeyInserter;
    private final ObjectMapper objectMapper;

    /**
     * 키 등록 시도. 이미 같은 키가 있고 요청 내용이 동일하면 기존 키를 반환합니다.
     */
    public IdempotencyAcquireResult acquire(String idempotencyKey, Object requestPayload) {
        String requestHash = sha256(serialize(requestPayload));

        // 1) 신규 등록 시도. unique 제약 위반은 race condition 발생을 의미합니다.
        try {
            IdempotencyKey saved = idempotencyKeyInserter.insert(IdempotencyKey.start(idempotencyKey, requestHash));
            log.info("IDEMPOTENCY_STORED keyPrefix={}", LogMasker.maskIdempotencyKey(idempotencyKey));
            return IdempotencyAcquireResult.newlyCreated(saved.getId());
        } catch (DataIntegrityViolationException e) {
            log.info("IDEMPOTENCY_DUPLICATE_DETECTED keyPrefix={}", LogMasker.maskIdempotencyKey(idempotencyKey));
        }

        // 2) 기존 키 조회 (insert가 실패한 세션과 분리된 새 트랜잭션에서 안전하게 수행).
        Optional<IdempotencyKey> existingOpt = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey);
        IdempotencyKey existing = existingOpt.orElseThrow(() -> new BusinessException(
                ErrorCode.INTERNAL_ERROR,
                "Idempotency key의 unique 충돌 후에도 레코드를 찾지 못했습니다."));

        if (!existing.getRequestHash().equals(requestHash)) {
            log.warn("IDEMPOTENCY_CONFLICT keyPrefix={}", LogMasker.maskIdempotencyKey(idempotencyKey));
            throw new BusinessException(ErrorCode.IDEMPOTENCY_CONFLICT);
        }

        if (existing.getStatus() == IdempotencyStatus.PROCESSING) {
            log.warn("IDEMPOTENCY_PROCESSING keyPrefix={}", LogMasker.maskIdempotencyKey(idempotencyKey));
            throw new BusinessException(ErrorCode.IDEMPOTENCY_PROCESSING);
        }

        // COMPLETED / FAILED → 기존 응답 그대로 사용
        return IdempotencyAcquireResult.existingResult(existing.getId(), existing.getResponseSnapshot());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(Long idempotencyKeyId, Object responseBody) {
        IdempotencyKey key = idempotencyKeyRepository.findById(idempotencyKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        key.markCompleted(serialize(responseBody));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long idempotencyKeyId, Object responseBody) {
        IdempotencyKey key = idempotencyKeyRepository.findById(idempotencyKeyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
        key.markFailed(serialize(responseBody));
    }

    public <T> T deserialize(String json, Class<T> type) {
        if (json == null) return null;
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "응답 역직렬화 실패: " + e.getMessage());
        }
    }

    private String serialize(Object value) {
        if (value == null) return "";
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "요청 직렬화 실패: " + e.getMessage());
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원 환경입니다.", e);
        }
    }

    public record IdempotencyAcquireResult(
            Long idempotencyKeyId,
            boolean isExisting,
            String existingResponseJson
    ) {
        public static IdempotencyAcquireResult newlyCreated(Long id) {
            return new IdempotencyAcquireResult(id, false, null);
        }
        public static IdempotencyAcquireResult existingResult(Long id, String responseJson) {
            return new IdempotencyAcquireResult(id, true, responseJson);
        }
    }
}
