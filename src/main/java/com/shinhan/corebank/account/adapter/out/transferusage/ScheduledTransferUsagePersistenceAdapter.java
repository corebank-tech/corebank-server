package com.shinhan.corebank.account.adapter.out.transferusage;

import com.shinhan.corebank.account.application.port.out.ScheduledTransferUsageQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ScheduledTransferUsagePersistenceAdapter
        implements ScheduledTransferUsageQueryPort {

    private static final List<String> BLOCKING_STATUSES =
            List.of(
                    ScheduledTransferStatus.WAITING.name(),
                    ScheduledTransferStatus.PROCESSING.name()
            );

    private final ScheduledTransferUsageJpaRepository repository;

    @Override
    public boolean existsUsingWithdrawalAccount(
            Long withdrawalAccountId
    ) {
        return repository
                .existsByWithdrawalAccountIdAndStatusIn(
                        withdrawalAccountId,
                        BLOCKING_STATUSES
                );
    }
}
