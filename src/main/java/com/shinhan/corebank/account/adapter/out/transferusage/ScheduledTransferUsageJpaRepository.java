package com.shinhan.corebank.account.adapter.out.transferusage;

import org.springframework.data.repository.Repository;

interface ScheduledTransferUsageJpaRepository
        extends Repository<
        ScheduledTransferUsageJpaEntity,
        Long
        > {

    boolean existsByWithdrawalAccountIdAndStatus(
            Long withdrawalAccountId,
            String status
    );
}
