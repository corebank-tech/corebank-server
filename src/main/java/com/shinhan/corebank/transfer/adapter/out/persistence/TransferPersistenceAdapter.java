package com.shinhan.corebank.transfer.adapter.out.persistence;

import com.shinhan.corebank.transfer.application.port.out.TransferSavePort;
import com.shinhan.corebank.transfer.domain.Transfer;
import org.springframework.stereotype.Component;

@Component
public class TransferPersistenceAdapter implements TransferSavePort {

    private final TransferJpaRepository repository;

    public TransferPersistenceAdapter(TransferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Transfer save(Transfer transfer) {
        TransferJpaEntity entity = TransferMapper.toEntity(transfer);
        TransferJpaEntity saved = repository.save(entity);
        return TransferMapper.toDomain(saved);
    }
}
