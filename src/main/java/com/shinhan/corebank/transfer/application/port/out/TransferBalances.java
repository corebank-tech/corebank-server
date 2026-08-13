package com.shinhan.corebank.transfer.application.port.out;

/**
 * {@link AccountLockPort#applyTransfer} 적용 후의 출금/입금 계좌 잔액.
 */
public record TransferBalances(long withdrawalBalanceAfter, long depositBalanceAfter) {
}
