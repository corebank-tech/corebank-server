package com.shinhan.corebank.account.application.port.out;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface AccountStatusHistoryPort {

    void recordMaturity(Long accountId, LocalDate maturityDate, LocalDate maturityBaseDate, LocalDateTime changedAt);

    /**
     * 고객 요청 만기해지로 MATURED -> CLOSED 전이된 이력을 저장한다.
     *
     * @param maturityDate 계약 만기일
     * @param closedTradeDate MATURITY 출금 원장의 실제 거래일
     * @param changedAt 실제 해지 시각
     */
    void recordMaturityClosure(
            Long accountId, LocalDate maturityDate, LocalDate closedTradeDate, LocalDateTime changedAt);
}
