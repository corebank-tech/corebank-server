package com.shinhan.infoapi.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// 적재 이력이 없으면 asOf·건수가 null 이다. 고객 식별자와 가명 키는 담지 않는다.
public record ProductSubscriptionSummary(
        LocalDate asOf, YearMonth month, Long subscribedCount, Long closedCount, List<ProductSubscriptionLine> items) {

    public static ProductSubscriptionSummary noLoadHistory(YearMonth month) {
        return new ProductSubscriptionSummary(null, month, null, null, List.of());
    }
}
