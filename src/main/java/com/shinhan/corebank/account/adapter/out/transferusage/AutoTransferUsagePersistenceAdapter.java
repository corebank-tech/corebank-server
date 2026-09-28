package com.shinhan.corebank.account.adapter.out.transferusage;

import com.shinhan.corebank.account.application.port.out.AutoTransferUsageQueryPort;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AutoTransferUsagePersistenceAdapter implements AutoTransferUsageQueryPort {

    private static final String NORMAL_STATUS = AutoTransferStatus.NORMAL.name();

    private static final String PROCESSING_STATUS = ProcessResultStatus.PROCESSING.name();

    private final AutoTransferUsageJpaRepository repository;

    @Override
    public boolean existsUsingWithdrawalAccount(Long withdrawalAccountId) {
        return repository.existsByWithdrawalAccountIdAndStatus(withdrawalAccountId, NORMAL_STATUS)
                || repository.existsExecutionByWithdrawalAccountIdAndStatus(withdrawalAccountId, PROCESSING_STATUS) > 0;
    }
}
