package com.shinhan.corebank.autotransfer.application.port.out;

import com.shinhan.corebank.autotransfer.domain.AutoTransfer;

import java.util.Optional;

public interface AutoTransferPersistencePort {
    AutoTransfer save(AutoTransfer autoTransfer);
    Optional<AutoTransfer> findById(Long autoTransferId);
    boolean existsActiveDuplicate(Long withdrawalAccountId, String depositAccountNumber, int transferDay);
}
