package com.shinhan.corebank.scheduledtransfer.adapter.out.persistence;

import com.shinhan.corebank.scheduledtransfer.application.port.out.ScheduledTransferPersistencePort;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransfer;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ScheduledTransferPersistenceAdapter implements ScheduledTransferPersistencePort {
    private final ScheduledTransferJpaRepository scheduledTransferJpaRepository;

    @Override
    public ScheduledTransfer save(ScheduledTransfer scheduledTransfer) {
        ScheduledTransferJpaEntity saved = scheduledTransferJpaRepository.save(ScheduledTransferMapper.toEntity(scheduledTransfer));
        return ScheduledTransferMapper.toDomain(saved);
    }

    @Override
    public Optional<ScheduledTransfer> findById(Long scheduledTransferId) {
        return scheduledTransferJpaRepository.findById(scheduledTransferId)
                .map(ScheduledTransferMapper::toDomain);
    }

    @Override
    public boolean existsActiveDuplicate(Long customerId, Long withdrawalAccountId, String payeeAccountNumber,
                                         Long amount, LocalDate scheduledDate) {
        return scheduledTransferJpaRepository
                .existsByCustomerIdAndWithdrawalAccountIdAndPayeeAccountNumberAndAmountAndScheduledDateAndStatus(
                        customerId, withdrawalAccountId, payeeAccountNumber, amount, scheduledDate,
                        ScheduledTransferStatus.WAITING);
    }
}
