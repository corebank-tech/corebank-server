package com.shinhan.corebank.account.adapter.out.transferusage;

import com.shinhan.corebank.account.application.port.out.AutoTransferUsageQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutoTransferUsagePersistenceAdapter
        implements AutoTransferUsageQueryPort {

    private static final String BLOCKING_STATUS =
            "NORMAL";

    private final AutoTransferUsageJpaRepository repository;

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