package com.shinhan.corebank.transfer.application.port.out;

/** 타행 전문 결과를 이체 상태로 옮긴 값. UNKNOWN만 처리 불명(TIMEOUT)으로 남고 나머지는 확정이다. */
public enum InterbankOutcome {
    /** 상대 은행이 입금했다. */
    APPROVED,
    /** 수취 계좌 없음(1001). */
    ACCOUNT_NOT_FOUND,
    /** 수취 계좌 거래 불가(1002). */
    ACCOUNT_UNAVAILABLE,
    /** 상대 은행이 처리하지 않았다 — 9999·1003·원거래 미수신(3001), 또는 보내지 못했다. */
    NOT_PROCESSED,
    /** 결과를 모른다 — 무응답·응답 중 끊김·중복거래(2001). 조회거래로 다시 확인한다. */
    UNKNOWN
}
