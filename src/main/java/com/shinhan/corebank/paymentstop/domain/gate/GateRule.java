package com.shinhan.corebank.paymentstop.domain.gate;

// 게이트 규칙과 그 근거 구분(D33). 화면 규칙표의 근거 열이 이 값을 쓴다.
public enum GateRule {
    SIMPLIFIED_EXCLUSION("지급정지 이력이 있으면 간소화 대상에서 제외", RuleBasis.REGULATOR_STANDARD),
    EXTERNAL_CHECK("수사기관과 관계 기관의 외부 확인 회신", RuleBasis.INTERNAL),
    FRAUD_PATTERN("다툼 거래 전후 다수 송금인 입금과 즉시 출금", RuleBasis.LAW_INTERNAL_METRIC),
    RELATIONSHIP("송금인과 명의인의 다툼 이전 지속 거래", RuleBasis.INTERNAL),
    PARTIAL_REQUIREMENT("소액이고 지급정지 이력이 없으며 다툼 이전 생활 거래가 있음", RuleBasis.REGULATOR_STANDARD);

    private final String description;
    private final RuleBasis basis;

    GateRule(String description, RuleBasis basis) {
        this.description = description;
        this.basis = basis;
    }

    public String description() {
        return description;
    }

    public RuleBasis basis() {
        return basis;
    }

    public enum RuleBasis {
        // 법 조항의 내부 수치화(사기 패턴은 법 제7조제1항제2호 단서의 거래행태)
        LAW_INTERNAL_METRIC,
        // 2026-05-04 당국 표준
        REGULATOR_STANDARD,
        // 내부 기준
        INTERNAL
    }
}
