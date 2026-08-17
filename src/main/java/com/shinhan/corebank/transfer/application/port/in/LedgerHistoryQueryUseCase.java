package com.shinhan.corebank.transfer.application.port.in;

// P2가 계좌 거래내역 조회 API(GET /api/v1/accounts/{accountId}/transactions)에서
// 소유권 검증·기간 기본값 적용 후 이 인터페이스만 보고 호출한다.
public interface LedgerHistoryQueryUseCase {
    LedgerHistoryResult query(LedgerHistoryQuery query);
}
