package com.shinhan.corebank.account.adapter.out.transferusage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

@Entity(name = "AccountScheduledTransferUsageJpaEntity")
@Table(name = "scheduled_transfer")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledTransferUsageJpaEntity {

    @Id
    @Column(name = "scheduled_transfer_id")
    private Long scheduledTransferId;

    @Column(name = "withdrawal_account_id")
    private Long withdrawalAccountId;

    @Column(name = "status")
    private String status;
}
