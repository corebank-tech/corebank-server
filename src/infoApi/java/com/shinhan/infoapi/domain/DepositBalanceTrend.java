package com.shinhan.infoapi.domain;

import java.time.LocalDate;
import java.util.List;

// asOf 는 적재가 빠짐없이 끝난 마지막 기준일이고, 적재 이력이 없으면 null 이다.
public record DepositBalanceTrend(LocalDate asOf, List<DepositBalancePoint> items) {

    public static DepositBalanceTrend noLoadHistory() {
        return new DepositBalanceTrend(null, List.of());
    }
}
