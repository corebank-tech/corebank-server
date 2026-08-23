package com.shinhan.corebank.limit.adapter.out.persistence;

import com.shinhan.corebank.common.entity.BaseEntity;
import com.shinhan.corebank.limit.domain.TransferLimit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "transfer_limit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferLimitJpaEntity extends BaseEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "one_time_limit", nullable = false)
    private long oneTimeLimit;

    @Column(name = "daily_limit", nullable = false)
    private long dailyLimit;

    TransferLimit toDomain() {
        return TransferLimit.restore(customerId, oneTimeLimit, dailyLimit);
    }
}
