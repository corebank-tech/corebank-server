package com.shinhan.corebank.account.adapter.out.transferusage;

import org.springframework.data.repository.Repository;

interface AutoTransferUsageJpaRepository
        extends Repository<
        AutoTransferUsageJpaEntity,
        Long
        > {

    boolean existsByWithdrawalAccountIdAndStatus(
            Long withdrawalAccountId,
            String status
    );
}