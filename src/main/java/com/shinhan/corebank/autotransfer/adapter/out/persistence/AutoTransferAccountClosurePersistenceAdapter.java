package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.application.port.out.AutoTransferAccountClosurePort;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AutoTransferAccountClosurePersistenceAdapter implements AutoTransferAccountClosurePort {

    private final AutoTransferJpaRepository autoTransferRepository;
    private final AutoTransferExecutionJpaRepository executionRepository;

    @Override
    public List<AutoTransfer> findNormalReferencingAccountForUpdate(Long accountId, String accountNumber) {

        return autoTransferRepository.findLinkedForUpdate(accountId, accountNumber, AutoTransferStatus.NORMAL).stream()
                .map(AutoTransferMapper::toDomain)
                .toList();
    }

    @Override
    public boolean hasProcessingExecutionReferencingAccount(Long accountId, String accountNumber) {

        return !executionRepository
                .findProcessingReferencingAccountForShare(accountId, accountNumber, ProcessResultStatus.PROCESSING)
                .isEmpty();
    }
}
