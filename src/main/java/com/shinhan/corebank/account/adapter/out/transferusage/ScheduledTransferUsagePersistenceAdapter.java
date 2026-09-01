package com.shinhan.corebank.account.adapter.out.transferusage;

import com.shinhan.corebank.account.application.port.out.ScheduledTransferUsageQueryPort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledTransferUsagePersistenceAdapter implements ScheduledTransferUsageQueryPort {

    private static final List<String> BLOCKING_STATUSES =
            List.of(ScheduledTransferStatus.WAITING.name(), ScheduledTransferStatus.PROCESSING.name());

    private final ScheduledTransferUsageJpaRepository repository;

    @Override
    public boolean existsUsingWithdrawalAccount(Long withdrawalAccountId) {
        return repository.existsByWithdrawalAccountIdAndStatusIn(withdrawalAccountId, BLOCKING_STATUSES);
    }
}
