package com.shinhan.corebank.subscription.adapter.in.baseline;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ph11-baseline")
public record AccumulatedDailyBalanceBaselineProperties(
        long accountIdStart, int accountCount, LocalDate fromInclusive, LocalDate toExclusive) {

    public AccumulatedDailyBalanceBaselineProperties {
        if (accountIdStart <= 0) {
            throw new IllegalArgumentException("accountIdStart는 양수여야 합니다.");
        }
        if (accountCount <= 0) {
            throw new IllegalArgumentException("accountCount는 양수여야 합니다.");
        }
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("적수 측정 기간이 올바르지 않습니다.");
        }
    }
}
