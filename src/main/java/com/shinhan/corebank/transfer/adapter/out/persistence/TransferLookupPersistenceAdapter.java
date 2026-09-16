package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.in.TransferResult;
import com.shinhan.corebank.transfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.transfer.domain.Transfer;
import com.shinhan.corebank.transfer.domain.TransferSourceType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TransferLookupPersistenceAdapter implements TransferLookupPort {

    private final TransferJpaRepository repository;

    public TransferLookupPersistenceAdapter(TransferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<TransferResult> findBySourceAndExecutionDate(
            TransferSourceType sourceType, Long sourceId, LocalDate executionDate) {
        return repository
                .findBySourceTypeAndSourceIdAndExecutionDate(sourceType, sourceId, executionDate)
                .map(entity -> TransferResult.builder()
                        .status(entity.getStatus())
                        .transactionNumber(entity.getTransactionNumber())
                        .transferredAt(entity.getTransferredAt())
                        .withdrawalBalanceAfter(entity.getWithdrawalBalanceAfter())
                        .errorCode(entity.getErrorCode())
                        .errorMessage(entity.getErrorMessage())
                        .build());
    }

    @Override
    public Optional<Transfer> findByTransactionNumber(String transactionNumber) {
        return repository.findByTransactionNumber(transactionNumber).map(TransferMapper::toDomain);
    }
}
