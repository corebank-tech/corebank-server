package com.shinhan.corebank.paymentstop.domain.notice;

public enum NoticeKind {
    RECEIPT,
    SUPPLEMENT_REQUEST,
    DECISION_ACCEPTED,
    DECISION_PARTIAL,
    DECISION_REJECTED,
    // 지급정지 종료(전부 해제)
    STOP_TERMINATED
}
