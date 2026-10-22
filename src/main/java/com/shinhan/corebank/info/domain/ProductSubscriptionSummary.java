package com.shinhan.corebank.info.domain;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

// 상품별 가입·해지 건수. 고객 식별자와 가명 키는 담지 않는다(REQ-ADM-033).
public record ProductSubscriptionSummary(
        LocalDate asOf, YearMonth month, long subscribedCount, long closedCount, List<Line> items) {

    public record Line(
            long productId,
            String productCode,
            String productName,
            String productGroup,
            long subscribedCount,
            long closedCount) {}
}
