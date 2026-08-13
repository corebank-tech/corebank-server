package com.shinhan.corebank.transfer.application.port.out;

/**
 * 입금계좌번호 해석 결과. 계좌 ID와 함께, 이체 시점에 transfer/ledger_entry에
 * 스냅샷으로 남겨야 하는 예금주명(payee_name)을 담는다.
 */
public record ResolvedPayee(Long accountId, String payeeName) {
}
