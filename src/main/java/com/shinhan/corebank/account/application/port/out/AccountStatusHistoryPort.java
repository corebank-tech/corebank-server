package com.shinhan.corebank.account.application.port.out;

import com.shinhan.corebank.account.domain.AccountStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface AccountStatusHistoryPort {

    void recordMaturity(Long accountId, LocalDate maturityDate, LocalDate maturityBaseDate, LocalDateTime changedAt);

    /**
     * 고객 요청 중도해지로 ACTIVE -> CLOSED 전이된 이력을 저장한다.
     *
     * @param accountId 해지 계좌 ID
     * @param maturityDate 계약 만기일
     * @param closedTradeDate TERMINATION 원장의 거래일
     * @param changedAt 실제 해지 시각
     */
    void recordEarlyClosure(Long accountId, LocalDate maturityDate, LocalDate closedTradeDate, LocalDateTime changedAt);
    /**
     * 고객 요청 만기해지로 MATURED -> CLOSED 전이된 이력을 저장한다.
     *
     * @param accountId 해지 계좌 ID
     * @param maturityDate 계약 만기일
     * @param closedTradeDate MATURITY 원장의 거래일
     * @param changedAt 실제 해지 시각
     */
    void recordMaturityClosure(
            Long accountId, LocalDate maturityDate, LocalDate closedTradeDate, LocalDateTime changedAt);

    /** 지급정지 적용, 전환, 해제로 계좌 상태가 바뀐 이력을 저장한다. 상태가 그대로면 부르지 않는다. */
    void recordPaymentStopTransition(
            Long accountId, AccountStatus previousStatus, AccountStatus newStatus, LocalDateTime changedAt);
}
