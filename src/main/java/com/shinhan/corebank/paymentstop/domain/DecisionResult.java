package com.shinhan.corebank.paymentstop.domain;

// 이의제기 심사결과. 해제 범위는 담당자가 고르지 않고 사유 조항이 정한다(법 제8조제1항제2호 단서).
public enum DecisionResult {
    // 수용. 지급정지 전부 해제
    ACCEPTED,
    // 일부 지급정지로 전환. 피해금만 보류액으로 남긴다
    PARTIAL,
    // 불수용. 지급정지 유지
    REJECTED
}
