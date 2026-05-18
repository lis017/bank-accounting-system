package com.kakaobank.accounting.expense.service;

import com.kakaobank.accounting.accounting.domain.JournalEntry;
import com.kakaobank.accounting.accounting.service.JournalEntryService;
import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import com.kakaobank.accounting.expense.domain.ExpenseExecutionHistory;
import com.kakaobank.accounting.expense.domain.ExpenseRequest;
import com.kakaobank.accounting.expense.domain.ExpenseRequestStatus;
import com.kakaobank.accounting.expense.dto.ExpenseRequestCreateRequest;
import com.kakaobank.accounting.expense.dto.ExpenseRequestResponse;
import com.kakaobank.accounting.expense.repository.ExpenseExecutionHistoryRepository;
import com.kakaobank.accounting.expense.repository.ExpenseRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 집행 요청 생명주기를 관리하는 도메인 서비스.
 *
 * 책임 분리:
 *   - 본 서비스는 "요청 생성/승인/조회/실패 저장/CLOSED 처리" 등 짧고 단일 트랜잭션으로 끝나는 작업을 담당합니다.
 *   - 실제 외부 ERP 호출이 포함된 "execute" 흐름은 ExpenseExecutionService에 분리했습니다.
 *     트랜잭션 경계를 다르게 잡아야 하기 때문입니다(외부 호출은 트랜잭션 밖).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExpenseRequestService {

    private final ExpenseRequestRepository expenseRequestRepository;
    private final ExpenseExecutionHistoryRepository historyRepository;
    private final JournalEntryService journalEntryService;

    @Transactional
    public Long create(ExpenseRequestCreateRequest request) {
        ExpenseRequest entity = ExpenseRequest.create(
                request.budgetId(),
                request.departmentId(),
                request.accountCodeId(),
                request.amount(),
                request.memo(),
                request.targetDate());
        ExpenseRequest saved = expenseRequestRepository.save(entity);
        log.info("EXPENSE_REQUEST_CREATED expenseRequestId={} budgetId={} amount={}",
                saved.getId(), saved.getBudgetId(), saved.getAmount());
        return saved.getId();
    }

    @Transactional
    public void approve(Long expenseRequestId) {
        ExpenseRequest entity = expenseRequestRepository.findById(expenseRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_REQUEST_NOT_FOUND));
        ExpenseRequestStatus before = entity.getStatus();
        entity.changeStatus(ExpenseRequestStatus.APPROVED);
        log.info("EXPENSE_APPROVED expenseRequestId={} before={} after={}",
                expenseRequestId, before, entity.getStatus());
    }

    @Transactional(readOnly = true)
    public ExpenseRequestResponse findById(Long expenseRequestId) {
        ExpenseRequest entity = expenseRequestRepository.findById(expenseRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_REQUEST_NOT_FOUND));
        Long journalEntryId = journalEntryService.findByExpenseRequestId(expenseRequestId)
                .map(JournalEntry::getId)
                .orElse(null);
        return ExpenseRequestResponse.of(entity, journalEntryId);
    }

    /**
     * 일마감 배치에서 호출되는 CLOSED 전이 메서드.
     * 단건 트랜잭션 단위로 실행해, 한 건 실패가 다른 건에 영향을 주지 않도록 설계할 수 있습니다.
     */
    @Transactional
    public void closeIfExecuted(Long expenseRequestId) {
        ExpenseRequest entity = expenseRequestRepository.findById(expenseRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_REQUEST_NOT_FOUND));
        ExpenseRequestStatus before = entity.getStatus();
        entity.changeStatus(ExpenseRequestStatus.CLOSED);
        log.info("EXPENSE_CLOSED expenseRequestId={} before={} after={}",
                expenseRequestId, before, entity.getStatus());
    }

    /**
     * 외부 ERP 실패 시 호출되는 실패 저장.
     *
     * - 상태를 EXECUTION_FAILED로 변경
     * - 이력 테이블에 실패 이력 1건 추가
     *
     * 이 메서드는 ERP 호출 완료 후 짧은 트랜잭션으로 호출합니다.
     */
    @Transactional
    public void markExecutionFailed(Long expenseRequestId, String failureReason) {
        ExpenseRequest entity = expenseRequestRepository.findById(expenseRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_REQUEST_NOT_FOUND));
        ExpenseRequestStatus before = entity.getStatus();
        entity.changeStatus(ExpenseRequestStatus.EXECUTION_FAILED);
        historyRepository.save(ExpenseExecutionHistory.failure(expenseRequestId, failureReason));
        log.info("EXPENSE_FAILED expenseRequestId={} before={} after={} reason={}",
                expenseRequestId, before, entity.getStatus(), failureReason);
    }

    /**
     * 외부 ERP 성공 시 EXECUTED 전환 + 이력 저장 + 회계전표 생성.
     *
     * 트랜잭션 안에서 모든 작업이 묶이므로, 회계전표 생성이 실패하면
     * 집행 상태 전환과 이력 저장도 함께 롤백됩니다.
     * → "EXECUTED인데 전표가 없는" 정합성 깨짐을 방지합니다.
     */
    @Transactional
    public Long markExecutedAndCreateJournal(Long expenseRequestId, String externalReferenceId) {
        ExpenseRequest entity = expenseRequestRepository.findById(expenseRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_REQUEST_NOT_FOUND));
        ExpenseRequestStatus before = entity.getStatus();
        entity.changeStatus(ExpenseRequestStatus.EXECUTED);
        historyRepository.save(ExpenseExecutionHistory.success(expenseRequestId, externalReferenceId));
        JournalEntry journalEntry = journalEntryService.createForExpense(entity);
        log.info("EXPENSE_EXECUTED expenseRequestId={} before={} after={} journalEntryId={}",
                expenseRequestId, before, entity.getStatus(), journalEntry.getId());
        return journalEntry.getId();
    }

    @Transactional
    public void increaseRetryCount(Long expenseRequestId) {
        ExpenseRequest entity = expenseRequestRepository.findById(expenseRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_REQUEST_NOT_FOUND));
        entity.increaseRetryCount();
    }

    @Transactional(readOnly = true)
    public ExpenseRequest getEntityOrThrow(Long expenseRequestId) {
        return expenseRequestRepository.findById(expenseRequestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.EXPENSE_REQUEST_NOT_FOUND));
    }
}
