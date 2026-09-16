package com.shinhan.corebank.transfer.application.port.in;

// 거래내역 조회 시 입출금 구분 필터. ledger_entry.direction(DEPOSIT/WITHDRAWAL)과 달리
// "전체"를 표현해야 해서 별도 enum으로 둔다.
public enum LedgerHistoryDirection {
    ALL,
    DEPOSIT,
    WITHDRAWAL
}
