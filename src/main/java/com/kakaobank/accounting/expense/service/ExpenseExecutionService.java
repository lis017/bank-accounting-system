package com.kakaobank.accounting.expense.service;

import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import com.kakaobank.accounting.common.util.LogMasker;
import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;
import com.kakaobank.accounting.expense.dto.ExpenseRequestExecuteRequest;
import com.kakaobank.accounting.expense.dto.ExpenseRequestResponse;
import com.kakaobank.accounting.idempotency.service.IdempotencyService;
import com.kakaobank.accounting.integration.erp.ErpClient;
import com.kakaobank.accounting.integration.erp.ErpPaymentRequest;
import com.kakaobank.accounting.integration.erp.ErpPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 집행 실행(execute)의 전체 흐름을 조율하는 서비스.
 *
 * 핵심 설계:
 *   1. 멱등성 키를 먼저 별도 트랜잭션(REQUIRES_NEW)으로 저장한다. 이로써 중복 호출을 즉시 차단.
 *   2. ExpensePreparationService.prepareExecution()으로 APPROVED 검증 + 예산 차감을 단일 트랜잭션으로 처리.
 *      - 이 단계에서 실패하면 멱등성 키를 FAILED로 마킹해 PROCESSING 고착을 방지한다.
 *      - prepareExecution은 self-invocation 회피를 위해 별도 빈으로 분리되어 있다.
 *   3. 외부 ERP 호출은 트랜잭션 밖에서 수행. (DB 커넥션을 외부 호출 시간 동안 점유하지 않기 위함)
 *   4. ERP 결과에 따라 다시 짧은 트랜잭션으로 상태/이력/전표를 저장.
 *   5. 멱등성 키의 응답 스냅샷을 마무리(REQUIRES_NEW)로 갱신.
 *
 * PROCESSING 고착 방지 설계 (방법 A):
 *   - 멱등성 키를 먼저 저장하면 같은 키의 동시 호출을 즉시 차단할 수 있다(핵심 의도 유지).
 *   - 단, prepareExecution(상태 검증, 예산 차감) 또는 그 이후 단계가 실패하면
 *     키를 반드시 FAILED로 마킹해야 한다.
 *   - 이렇게 하면 클라이언트가 오류를 수정한 뒤(예: 잔액 충전, 상태 수정) 같은 키로 재시도할 수 없다.
 *     재시도가 필요하다면 새 Idempotency-Key를 발급해야 한다(표준적인 멱등성 키 정책).
 *
 * 면접 포인트:
 *   - "외부 API 호출을 트랜잭션 안에 두면 안 되는 이유"는 README 면접질문에 정리되어 있습니다.
 *   - "예산 차감 후 ERP 실패" 시점에 잔액을 복원하지 않는 이유는,
 *     실패 후에도 retry로 재시도할 가능성이 높고, 매번 잔액을 흔드는 것보다
 *     "EXECUTION_FAILED" 상태로 잠시 묶어두는 편이 데이터 정합성에 유리하다고 판단했기 때문입니다.
 *     (운영 정책으로 "최종 거절"이 결정되면 별도 보정 트랜잭션으로 복원하는 것을 README에 TODO로 남겼습니다.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseExecutionService {

    private final ExpenseRequestService expenseRequestService;
    private final ExpensePreparationService expensePreparationService;
    private final ErpClient erpClient;
    private final IdempotencyService idempotencyService;

    /**
     * 집행 실행 API의 진입점.
     *
     * 반환되는 ExpenseRequestResponse는 컨트롤러가 그대로 응답합니다.
     */
    public ExpenseRequestResponse execute(Long expenseRequestId,
                                          String idempotencyKey,
                                          ExpenseRequestExecuteRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Idempotency-Key 헤더가 필요합니다.");
        }
        log.info("EXPENSE_EXECUTE_START expenseRequestId={} keyPrefix={}",
                expenseRequestId, LogMasker.maskIdempotencyKey(idempotencyKey));

        // 1) 멱등성 키 획득. 같은 키/같은 요청 + COMPLETED면 기존 응답 그대로 반환.
        var idempotencyPayload = new IdempotencyPayload(expenseRequestId, request);
        var acquireResult = idempotencyService.acquire(idempotencyKey, idempotencyPayload);
        if (acquireResult.isExisting()) {
            log.info("EXPENSE_EXECUTE_RETURN_CACHED expenseRequestId={} keyPrefix={}",
                    expenseRequestId, LogMasker.maskIdempotencyKey(idempotencyKey));
            return idempotencyService.deserialize(acquireResult.existingResponseJson(), ExpenseRequestResponse.class);
        }

        // 2) 사전 검증 + 예산 차감. 별도 빈(ExpensePreparationService)을 통해 단일 트랜잭션으로 수행.
        //    self-invocation 시 CGLIB 프록시가 우회되어 @Transactional이 적용되지 않으므로,
        //    로직을 별도 Spring 빈으로 분리했습니다.
        //    실패 시 멱등성 키를 FAILED로 마킹해 PROCESSING 고착을 방지합니다.
        try {
            expensePreparationService.prepareExecution(expenseRequestId);
        } catch (Exception prepareFail) {
            log.warn("EXPENSE_PREPARE_FAIL expenseRequestId={} reason={} — idempotency key will be marked FAILED",
                    expenseRequestId, prepareFail.getMessage());
            idempotencyService.markFailed(acquireResult.idempotencyKeyId(),
                    new PreparationFailureResponse(prepareFail.getMessage()));
            throw prepareFail;
        }

        // 3) 외부 ERP 호출 (트랜잭션 밖).
        ExpenseRequest snapshot = expenseRequestService.getEntityOrThrow(expenseRequestId);
        ErpPaymentResponse erpResponse = erpClient.requestPayment(new ErpPaymentRequest(
                snapshot.getId(),
                snapshot.getAmount(),
                request.executionNote()));

        // 4) 결과를 짧은 트랜잭션으로 반영.
        ExpenseRequestResponse responseBody;
        try {
            if (erpResponse.success()) {
                Long journalEntryId = expenseRequestService.markExecutedAndCreateJournal(
                        expenseRequestId, erpResponse.externalReferenceId());
                ExpenseRequest fresh = expenseRequestService.getEntityOrThrow(expenseRequestId);
                responseBody = ExpenseRequestResponse.of(fresh, journalEntryId);
                idempotencyService.markCompleted(acquireResult.idempotencyKeyId(), responseBody);
            } else {
                expenseRequestService.markExecutionFailed(expenseRequestId, erpResponse.failureReason());
                idempotencyService.markFailed(acquireResult.idempotencyKeyId(),
                        new ExternalFailureResponse(erpResponse.failureReason()));
                throw new BusinessException(ErrorCode.EXTERNAL_ERP_FAILED, erpResponse.failureReason());
            }
        } catch (BusinessException be) {
            throw be;
        } catch (Exception unexpected) {
            // ERP는 성공했지만 전표 저장 등에서 예외가 난 경우.
            // 멱등성 키는 FAILED로 마킹해 동일 요청이 재시도되도록 합니다.
            log.error("EXPENSE_EXECUTE_POST_ERP_FAIL expenseRequestId={}", expenseRequestId, unexpected);
            expenseRequestService.markExecutionFailed(expenseRequestId, unexpected.getMessage());
            idempotencyService.markFailed(acquireResult.idempotencyKeyId(),
                    new ExternalFailureResponse(unexpected.getMessage()));
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, unexpected.getMessage());
        }

        return responseBody;
    }

    /**
     * 실패 건 재처리.
     *
     * 흐름:
     *   - EXECUTION_FAILED 상태인 요청에 대해 다시 ERP 호출을 시도합니다.
     *   - 성공 시 EXECUTED + 전표 생성, 실패 시 retryCount만 증가합니다.
     *   - 예산은 이미 차감되어 있으므로 다시 차감하지 않습니다.
     */
    public ExpenseRequestResponse retry(Long expenseRequestId) {
        log.info("EXPENSE_RETRY_START expenseRequestId={}", expenseRequestId);
        ExpenseRequest entity = expenseRequestService.getEntityOrThrow(expenseRequestId);
        if (entity.getStatus() != ExpenseRequestStatus.EXECUTION_FAILED) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "retry는 EXECUTION_FAILED 상태에서만 가능합니다. 현재=" + entity.getStatus());
        }

        ErpPaymentResponse erpResponse = erpClient.requestPayment(new ErpPaymentRequest(
                entity.getId(),
                entity.getAmount(),
                "retry"));

        if (erpResponse.success()) {
            Long journalEntryId = expenseRequestService.markExecutedAndCreateJournal(
                    expenseRequestId, erpResponse.externalReferenceId());
            ExpenseRequest fresh = expenseRequestService.getEntityOrThrow(expenseRequestId);
            log.info("EXPENSE_RETRY_OK expenseRequestId={} journalEntryId={}", expenseRequestId, journalEntryId);
            return ExpenseRequestResponse.of(fresh, journalEntryId);
        } else {
            expenseRequestService.increaseRetryCount(expenseRequestId);
            log.warn("EXPENSE_RETRY_FAIL expenseRequestId={} reason={}", expenseRequestId, erpResponse.failureReason());
            throw new BusinessException(ErrorCode.EXTERNAL_ERP_FAILED, erpResponse.failureReason());
        }
    }

    /** 멱등성 키 해시 계산용 요청 스냅샷. */
    record IdempotencyPayload(Long expenseRequestId, ExpenseRequestExecuteRequest request) { }

    /** ERP 호출 실패 응답 저장용. */
    record ExternalFailureResponse(String failureReason) { }

    /**
     * prepareExecution(상태 검증, 예산 차감) 실패 응답 저장용.
     *
     * ExternalFailureResponse와 타입을 구분해두면 idempotency_key 테이블의
     * response_snapshot 을 보고 어느 단계에서 실패했는지 즉시 파악할 수 있습니다.
     */
    record PreparationFailureResponse(String failureReason) { }
}
