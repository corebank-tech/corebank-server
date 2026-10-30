package com.shinhan.corebank.account.api;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 만기해지의 계좌 잔액 확인 및 최종 종료를 위한 공개 계약. */
public interface MaturityAccountSettlementUseCase {

    /**
     * 계좌 소유권을 확인하고 현재 원장 잔액을 반환한다.
     *
     * 만기해지에서는 이자 지급 이후 호출한다.
     */
    long requireCurrentBalance(Long accountId, Long customerId);

    /**
     * 원리금 이체 후 상품 계좌를 CLOSED로 전환한다.
     *
     * 반드시 호출자 트랜잭션 안에서 실행한다.
     * closedTradeDate는 MATURITY 원장 이체 결과의 거래일이다.
     */
    void closeMatured(Long accountId, Long customerId, LocalDateTime closedAt, LocalDate closedTradeDate);
}
