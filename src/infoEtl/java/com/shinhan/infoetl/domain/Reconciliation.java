package com.shinhan.infoetl.domain;

import java.time.LocalDate;

// 적재 한 번의 대조 결과. GL 대조는 직전 COMPLETED일이 없으면 생략되어 뒤 세 값이 null이다(설계 §4).
public record Reconciliation(
        long ledgerTotal, LocalDate glCompareFromDate, Long martBalanceMovement, Long glDepositMovement) {

    public static Reconciliation ledgerOnly(long ledgerTotal) {
        return new Reconciliation(ledgerTotal, null, null, null);
    }

    public boolean glMatches() {
        return glDepositMovement == null || glDepositMovement.equals(martBalanceMovement);
    }
}
