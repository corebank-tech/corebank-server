package com.shinhan.corebank.info.domain;

import java.time.LocalDate;
import java.util.List;

// 일별 수신 잔액 추이. asOf 는 적재가 빠짐없이 끝난 마지막 기준일이고, 적재 이력이 없으면 null 이다.
public record DepositBalanceTrend(LocalDate asOf, List<Point> items) {

    public static DepositBalanceTrend noLoadHistory() {
        return new DepositBalanceTrend(null, List.of());
    }

    public record Point(LocalDate baseDate, long totalBalance) {}
}
