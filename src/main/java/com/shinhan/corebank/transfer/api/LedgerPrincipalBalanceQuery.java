package com.shinhan.corebank.transfer.api;

/**
 * 계좌 원장에서 이자 거래를 제외한 순입금액을 조회한다.
 *
 * 정정 원장(REVERSAL)은 원거래와 함께 합산하여
 * 실제 잔액 증감을 반영한다.
 */
public interface LedgerPrincipalBalanceQuery {

    long sumNetAmountExcludingInterest(long accountId);
}
