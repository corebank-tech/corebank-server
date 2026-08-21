package com.shinhan.corebank.account.adapter.out.transferusage;

import org.springframework.data.repository.Repository;

import java.util.Collection;

interface ScheduledTransferUsageJpaRepository
        extends Repository<
        ScheduledTransferUsageJpaEntity,
        Long
        > {

    boolean existsByWithdrawalAccountIdAndStatusIn(
            Long withdrawalAccountId,
            Collection<String> statuses
    );
}

