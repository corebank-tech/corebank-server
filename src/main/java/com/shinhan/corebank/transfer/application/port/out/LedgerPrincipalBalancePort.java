package com.shinhan.corebank.transfer.application.port.out;

/**
 * 계좌 원장에서 이자 거래를 제외한 순입금액을 조회한다.
 */
public interface LedgerPrincipalBalancePort {

    long sumNetAmountExcludingInterest(long accountId);
}
