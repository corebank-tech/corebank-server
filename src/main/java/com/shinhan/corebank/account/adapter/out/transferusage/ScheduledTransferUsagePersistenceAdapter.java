package com.shinhan.corebank.account.adapter.out.transferusage;

import com.shinhan.corebank.account.application.port.out.ScheduledTransferUsageQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledTransferUsagePersistenceAdapter
        implements ScheduledTransferUsageQueryPort {

    private static final String BLOCKING_STATUS =
            "WAITING";

    private final ScheduledTransferUsageJpaRepository repository;

    @Override
    public boolean existsUsingWithdrawalAccount(
            Long withdrawalAccountId
    ) {
        return repository
                .existsByWithdrawalAccountIdAndStatus(
                        withdrawalAccountId,
                        BLOCKING_STATUS
                );
    }
}