package com.shinhan.corebank.gl.domain;

/** 계정 5분류. 계정과목 코드 첫 자리와 1:1 로 대응한다 (1 자산 / 2 부채 / 3 자본 / 4 수익 / 5 비용). */
public enum GlAccountClass {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}
