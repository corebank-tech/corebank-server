package com.shinhan.corebank.autotransfer.domain;


import com.shinhan.corebank.common.exception.BusinessException;

import java.util.Arrays;

public enum TransferCycle {
    MONTHLY(1), QUARTERLY(3), SEMI_ANNUAL(6);

    // 필드 생성
    private final int months;
    TransferCycle(int months) {this.months = months;}
    public int months() { return months; }

    // DB에서 불러올 때
    public static TransferCycle fromMonths(int months) {
        return Arrays.stream(values())  // MONTHLY, QUARTERLY, SEMI_ANNUAL 나열
                .filter(c -> c.months == months) // 찾는 숫자 필터링
                .findFirst()    //
                .orElseThrow(() -> new BusinessException(AutoTransferErrorCode.INVALID_CYCLE_MONTHS));    // 예외처리
    }
}
