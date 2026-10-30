package com.shinhan.corebank.subscription.domain;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 적수 계산에 필요한 원장 잔액 시점 정보.
 *
 * <p>PH-11 시점에는 transfer 공개 조회 계약에 tradeDate가 아직 노출되지 않으므로
 * date에는 occurredAt 기준 날짜를 사용한다.
 * PH-41 연동 후에는 실제 거래일(tradeDate) 기준으로 교체한다.
 */
public record LedgerBalancePoint(LocalDate date, LocalDateTime occurredAt, long ledgerEntryId, long balanceAfter) {

    public LedgerBalancePoint {
        if (date == null || occurredAt == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }
    }
}
