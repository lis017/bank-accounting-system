package com.kakaobank.accounting.accounting;

import com.kakaobank.accounting.accounting.domain.JournalEntry;
import com.kakaobank.accounting.accounting.domain.JournalLine;
import com.kakaobank.accounting.common.exception.BusinessException;
import com.kakaobank.accounting.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * [4] 차변/대변 불일치 시 예외가 발생하는지 검증.
 *
 * 도메인 단위 테스트로 분리: Spring Context를 띄울 필요 없이 엔티티 메서드만 검증합니다.
 * 이렇게 분리하면 회계 규칙 변경 시 빠르게 회귀 검증이 가능합니다.
 */
class JournalEntryBalanceTest {

    @Test
    @DisplayName("[4] 차변과 대변 합계가 다르면 JOURNAL_NOT_BALANCED 예외가 발생한다")
    void throwsWhenDebitNotEqualToCredit() {
        JournalEntry entry = JournalEntry.create(1L, LocalDate.now());
        entry.addLine(JournalLine.debit(10L, 1000L));
        entry.addLine(JournalLine.credit(20L, 999L));

        assertThatThrownBy(entry::validateBalanced)
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getErrorCode())
                .isEqualTo(ErrorCode.JOURNAL_NOT_BALANCED);
    }

    @Test
    @DisplayName("차변과 대변 합계가 같으면 예외가 발생하지 않는다")
    void doesNotThrowWhenBalanced() {
        JournalEntry entry = JournalEntry.create(1L, LocalDate.now());
        entry.addLine(JournalLine.debit(10L, 1000L));
        entry.addLine(JournalLine.credit(20L, 1000L));

        assertThatCode(entry::validateBalanced).doesNotThrowAnyException();
    }
}
