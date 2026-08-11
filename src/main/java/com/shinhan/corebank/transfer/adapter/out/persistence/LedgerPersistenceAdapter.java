package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import com.shinhan.corebank.transfer.domain.LedgerPair;

import org.springframework.stereotype.Component;

@Component
public class LedgerPersistenceAdapter implements LedgerSavePort {

    private final LedgerEntryJpaRepository repository;
    private final LedgerEntryIdGenerator ledgerEntryIdGenerator;

    public LedgerPersistenceAdapter(
            LedgerEntryJpaRepository repository,
            LedgerEntryIdGenerator ledgerEntryIdGenerator
    ) {
        this.repository = repository;
        this.ledgerEntryIdGenerator = ledgerEntryIdGenerator;
    }

    @Override
    public void save(LedgerPair pair) {
        saveEntry(pair.getWithdrawalEntry());
        saveEntry(pair.getDepositEntry());
    }

    private void saveEntry(LedgerEntry entry) {
        LedgerEntryJpaEntity entity = LedgerEntryMapper.toEntity(entry)
                .toBuilder()
                .ledgerEntryId(ledgerEntryIdGenerator.nextId())
                .build();
        repository.save(entity);
    }
}
