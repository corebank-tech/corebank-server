package com.shinhan.corebank.account.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 원리금 TERMINATION 이체 후 같은 트랜잭션 안에서 ACTIVE 계좌를 종료한다. */
public interface EarlyTerminationAccountSettlementUseCase {
    void closeEarly(Long accountId, Long customerId, LocalDateTime closedAt, LocalDate closedTradeDate);
}
