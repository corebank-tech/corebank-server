package com.shinhan.corebank.subscription.adapter.in.baseline;

import java.time.LocalDate;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ph13-measure")
public record Ph13MeasurementProperties(
        long accountIdStart, int accountCount, int batchSize, LocalDate fromInclusive, LocalDate toExclusive) {

    public Ph13MeasurementProperties {
        if (accountIdStart <= 0) {
            throw new IllegalArgumentException("accountIdStart는 양수여야 합니다.");
        }

        if (accountCount <= 0) {
            throw new IllegalArgumentException("accountCount는 양수여야 합니다.");
        }

        if (batchSize <= 0 || batchSize > 1000) {
            throw new IllegalArgumentException("batchSize는 1~1000이어야 합니다.");
        }

        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)) {
            throw new IllegalArgumentException("적수 측정 기간이 올바르지 않습니다.");
        }

        Math.addExact(accountIdStart, accountCount - 1L);
    }
}
