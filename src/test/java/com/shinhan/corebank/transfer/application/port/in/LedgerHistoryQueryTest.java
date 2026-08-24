package com.shinhan.corebank.transfer.application.port.in;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;

class LedgerHistoryQueryTest {

    @Test
    @DisplayName("accountId가 없으면 REQUIRED_FIELD_MISSING 에러가 발생한다.")
    void missingAccountId_ThrowsException() {
        assertThatThrownBy(() -> LedgerHistoryQuery.builder()
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 8, 1))
                .direction(LedgerHistoryDirection.ALL)
                .sort(LedgerHistorySort.LATEST)
                .page(0)
                .size(10)
                .build())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.REQUIRED_FIELD_MISSING.getMessage());
    }

    @Test
    @DisplayName("조회기간이 없으면 REQUIRED_FIELD_MISSING 에러가 발생한다.")
    void missingDateRange_ThrowsException() {
        assertThatThrownBy(() -> LedgerHistoryQuery.builder()
                .accountId(1L)
                .direction(LedgerHistoryDirection.ALL)
                .sort(LedgerHistorySort.LATEST)
                .page(0)
                .size(10)
                .build())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.REQUIRED_FIELD_MISSING.getMessage());
    }

    @Test
    @DisplayName("size가 0 이하이면 INVALID_INPUT 에러가 발생한다.")
    void nonPositiveSize_ThrowsException() {
        assertThatThrownBy(() -> LedgerHistoryQuery.builder()
                .accountId(1L)
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 8, 1))
                .direction(LedgerHistoryDirection.ALL)
                .sort(LedgerHistorySort.LATEST)
                .page(0)
                .size(0)
                .build())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.INVALID_INPUT.getMessage());
    }

    @Test
    @DisplayName("all=true면 size가 0 이하여도 정상 생성된다.")
    void allTrue_skipsSizeValidation() {
        assertDoesNotThrow(() -> LedgerHistoryQuery.builder()
                .accountId(1L)
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 8, 1))
                .direction(LedgerHistoryDirection.ALL)
                .sort(LedgerHistorySort.LATEST)
                .page(0)
                .size(0)
                .all(true)
                .build());
    }

    @Test
    @DisplayName("page가 음수이면 INVALID_INPUT 에러가 발생한다.")
    void negativePage_ThrowsException() {
        assertThatThrownBy(() -> LedgerHistoryQuery.builder()
                .accountId(1L)
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 8, 1))
                .direction(LedgerHistoryDirection.ALL)
                .sort(LedgerHistorySort.LATEST)
                .page(-1)
                .size(10)
                .build())
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(CommonErrorCode.INVALID_INPUT.getMessage());
    }

    @Test
    @DisplayName("keyword 없이도 정상 생성된다 (선택값).")
    void validQueryWithoutKeyword_Success() {
        assertDoesNotThrow(() -> LedgerHistoryQuery.builder()
                .accountId(1L)
                .fromDate(LocalDate.of(2026, 7, 1))
                .toDate(LocalDate.of(2026, 8, 1))
                .direction(LedgerHistoryDirection.ALL)
                .sort(LedgerHistorySort.LATEST)
                .page(0)
                .size(10)
                .build());
    }
}
