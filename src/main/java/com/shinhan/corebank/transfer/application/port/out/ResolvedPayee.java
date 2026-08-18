package com.shinhan.corebank.transfer.application.port.out;

/**
 * 입금계좌번호 해석 결과. 계좌 ID와 함께, 이체 시점에 transfer/ledger_entry에
 * 스냅샷으로 남겨야 하는 예금주명(payee_name), 입금계좌 상품유형 검증(TRF0004)에 쓰는
 * accountType, 그리고 예금주 조회 API의 미존재(TRF0201)/정지·해지(TRF0301) 구분에 쓰는
 * status를 담는다.
 */
public record ResolvedPayee(Long accountId, String payeeName, LockedAccountType accountType, LockedAccountStatus status) {
}
