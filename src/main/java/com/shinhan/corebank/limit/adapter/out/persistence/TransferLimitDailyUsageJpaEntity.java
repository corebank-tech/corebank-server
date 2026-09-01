package com.shinhan.corebank.limit.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.limit.domain.TransferLimitDailyUsage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
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

    /** KST 영업일. 시각이 아니라 날짜만 담으므로 자정 경계로 집계가 갈린다. */
    @Id
    @Column(name = "usage_date")
    private LocalDate usageDate;

    @Column(name = "used_amount", nullable = false)
    private long usedAmount;

    TransferLimitDailyUsage toDomain() {
        return TransferLimitDailyUsage.restore(customerId, usageDate, usedAmount);
    }

    void apply(long usedAmount) {
        this.usedAmount = usedAmount;
    }
}
