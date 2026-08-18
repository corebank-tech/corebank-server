package com.shinhan.corebank.autotransfer.adapter.out.persistence;

import com.shinhan.corebank.autotransfer.application.port.out.TransferLookupPort;
import com.shinhan.corebank.autotransfer.application.port.out.TransferLookupResult;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;

@Component
public class TransferLookupAdapter implements TransferLookupPort {

    private static final String SOURCE_TYPE_AUTO = "AUTO";
    private final TransferLookupJpaRepository transferLookupJpaRepository;

    public TransferLookupAdapter(TransferLookupJpaRepository transferLookupJpaRepository) {
        this.transferLookupJpaRepository = transferLookupJpaRepository;
    }

    @Override
    public Optional<TransferLookupResult> findBySourceAndDate(Long autoTransferId, LocalDate executionDate) {
        return transferLookupJpaRepository.findBySourceAndDate(SOURCE_TYPE_AUTO, autoTransferId, executionDate.atStartOfDay(),
                executionDate.plusDays(1).atStartOfDay())
                .map(e -> new TransferLookupResult(e.getTransactionNumber(), e.getStatus(), e.getErrorMessage()));
    }

}
