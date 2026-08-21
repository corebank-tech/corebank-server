package com.shinhan.corebank.scheduledtransfer.adapter.out.limit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity(name = "ScheduledTransferTransferLimitJpaEntity")
@Table(name = "transfer_limit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TransferLimitJpaEntity {

    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "one_time_limit", nullable = false)
    private long oneTimeLimit;
}
