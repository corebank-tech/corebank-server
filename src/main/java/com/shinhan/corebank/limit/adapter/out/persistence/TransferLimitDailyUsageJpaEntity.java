package com.shinhan.corebank.limit.adapter.out.persistence;

import java.time.LocalDate;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * (customer_id, usage_date) 복합 PK 다. 한도 검증 시 이 행에 비관적 락을 걸어
 * 1일 누적 집계와 이체 실행을 같은 트랜잭션으로 묶는다(REQ-TRSF-012).
 */
@Entity
@Table(name = "transfer_limit_daily_usage")
@IdClass(TransferLimitDailyUsageId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferLimitDailyUsageJpaEntity extends BaseEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    /** KST 영업일. UTC 로 산출하면 매일 09시 이전 이체가 전날에 붙는다. */
    @Id
    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "used_amount", nullable = false)
    private long usedAmount;

    private TransferLimitDailyUsageJpaEntity(TransferLimitDailyUsage usage) {
        this.customerId = usage.getCustomerId();
        this.usageDate = usage.getUsageDate();
        this.usedAmount = usage.getUsedAmount();
    }

    static TransferLimitDailyUsageJpaEntity from(TransferLimitDailyUsage usage) {
        return new TransferLimitDailyUsageJpaEntity(usage);
    }

    TransferLimitDailyUsage toDomain() {
        return TransferLimitDailyUsage.restore(customerId, usageDate, usedAmount);
    }

    void apply(long usedAmount) {
        this.usedAmount = usedAmount;
    }
}
