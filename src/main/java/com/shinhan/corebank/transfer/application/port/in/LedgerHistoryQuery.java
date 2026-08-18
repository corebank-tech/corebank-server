package com.shinhan.corebank.transfer.application.port.in;

import java.time.LocalDate;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;

import lombok.Builder;

/**
 * P2가 계좌 소유권 검증·기간 기본값·1-based→0-based page 변환까지 마친 뒤 전달하는
 * 정규화된 조회 조건. {@code fromDate > toDate}, 최대 조회기간 같은 화면 단 검증은
 * P2 책임이라 여기서 다시 하지 않는다 — 이 생성자는 공개 계약의 경계에서 null만 막는다.
 */
@Builder
public record LedgerHistoryQuery(
        Long accountId,
        LocalDate fromDate,
        LocalDate toDate,
        LedgerHistoryDirection direction,
        String keyword,
        LedgerHistorySort sort,
        int page,
        int size
) {

    public LedgerHistoryQuery {
        if (accountId == null || fromDate == null || toDate == null
                || direction == null || sort == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
        if (size <= 0 || page < 0) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
