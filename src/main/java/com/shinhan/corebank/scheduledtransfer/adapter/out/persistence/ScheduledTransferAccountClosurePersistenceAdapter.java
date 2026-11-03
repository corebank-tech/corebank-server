package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferAccountClosurePort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ScheduledTransferAccountClosurePersistenceAdapter implements ScheduledTransferAccountClosurePort {

    private final ScheduledTransferJpaRepository repository;

    @Override
    public List<ScheduledTransfer> findPendingReferencingAccountForUpdate(Long accountId, String accountNumber) {

        return repository
                .findLinkedForUpdate(
                        accountId,
                        accountNumber,
                        List.of(ScheduledTransferStatus.WAITING, ScheduledTransferStatus.PROCESSING))
                .stream()
                .map(ScheduledTransferMapper::toDomain)
                .toList();
    }
}
