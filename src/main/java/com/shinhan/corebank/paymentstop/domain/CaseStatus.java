package com.shinhan.corebank.paymentstop.domain;

// 사건 기준 지급정지 상태. 계좌 상태와 보류액의 정본은 account 도메인이다.
public enum CaseStatus {
    STOPPED,
    PARTIAL,
    RELEASED
}
