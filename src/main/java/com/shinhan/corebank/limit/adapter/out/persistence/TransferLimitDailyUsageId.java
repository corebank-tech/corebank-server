package com.shinhan.corebank.limit.adapter.out.persistence;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/** transfer_limit_daily_usage 복합 PK 클래스. */
public class TransferLimitDailyUsageId implements Serializable {

    private Long customerId;
    private LocalDate usageDate;

    protected TransferLimitDailyUsageId() {}

    public TransferLimitDailyUsageId(Long customerId, LocalDate usageDate) {
        this.customerId = customerId;
        this.usageDate = usageDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransferLimitDailyUsageId other)) {
            return false;
        }
        return Objects.equals(customerId, other.customerId) && Objects.equals(usageDate, other.usageDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(customerId, usageDate);
    }
}
