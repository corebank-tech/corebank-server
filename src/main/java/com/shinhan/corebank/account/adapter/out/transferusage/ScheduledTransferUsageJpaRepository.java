package com.shinhan.corebank.account.adapter.out.transferusage;

import java.util.Collection;
import org.springframework.data.repository.Repository;

interface ScheduledTransferUsageJpaRepository extends Repository<ScheduledTransferUsageJpaEntity, Long> {

    boolean existsByWithdrawalAccountIdAndStatusIn(Long withdrawalAccountId, Collection<String> statuses);
}
