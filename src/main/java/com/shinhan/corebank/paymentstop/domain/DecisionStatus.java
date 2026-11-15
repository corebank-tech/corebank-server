package com.shinhan.corebank.paymentstop.domain;

// 판정 행의 상태(D13). 불수용은 작성 즉시 CONFIRMED, 수용과 일부 지급정지는 PENDING_APPROVAL 에서 시작한다.
public enum DecisionStatus {
    PENDING_APPROVAL,
    CONFIRMED,
    // 승인자가 반려. 이의제기는 심사 중으로 돌아가고 판정을 다시 쓴다
    RETURNED
}
