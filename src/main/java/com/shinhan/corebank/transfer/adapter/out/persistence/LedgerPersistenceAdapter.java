package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.out.LedgerSavePort;
import com.shinhan.corebank.transfer.domain.LedgerEntry;
import com.shinhan.corebank.transfer.domain.LedgerPair;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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

    // 포트 계약(원장 2행 원자적 저장)을 호출자의 트랜잭션 유무와 무관하게 이 어댑터 스스로 보장한다.
    @Override
    @Transactional
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
