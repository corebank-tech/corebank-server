package com.shinhan.corebank.paymentstop.domain.gate;

// 게이트가 낸 갈래. 담당자 판정의 선택지를 연다.
public enum GateBranch {
    // 불수용 후보. 사기 패턴이 있고 정상 근거가 없다
    FRAUD_PATTERN,
    // 수용 후보. 관계 인정 등 정상 근거가 있다
    NORMAL_ACCEPT,
    // 일부 지급정지 후보(간소화 요건 충족, PA-09)
    NORMAL_PARTIAL,
    // 판단 불가. 근거가 없거나 신호가 충돌한다. 외부 확인 요청이나 심층 심사로 간다
    UNDETERMINED
}
