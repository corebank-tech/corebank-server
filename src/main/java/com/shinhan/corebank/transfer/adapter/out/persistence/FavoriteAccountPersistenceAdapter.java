package com.shinhan.corebank.transfer.adapter.out.persistence;

import java.util.List;

import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;

import org.springframework.stereotype.Component;

@Component
public class FavoriteAccountPersistenceAdapter implements FavoriteAccountPersistencePort {

    private final FavoriteAccountJpaRepository repository;

    public FavoriteAccountPersistenceAdapter(FavoriteAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public FavoriteAccount save(FavoriteAccount favoriteAccount) {
        FavoriteAccountJpaEntity saved = repository.save(toEntity(favoriteAccount));
        return toDomain(saved);
    }

    @Override
    public long countByCustomerId(Long customerId) {
        return repository.countByCustomerId(customerId);
    }

    @Override
    public List<FavoriteAccount> findAllByCustomerId(Long customerId) {
        return repository.findAllByCustomerIdOrderByRegisteredAtDesc(customerId).stream()
                .map(this::toDomain)
                .toList();
    }

    private FavoriteAccountJpaEntity toEntity(FavoriteAccount favoriteAccount) {
        return FavoriteAccountJpaEntity.builder()
                .favoriteAccountId(favoriteAccount.getFavoriteAccountId())
                .customerId(favoriteAccount.getCustomerId())
                .depositAccountNumber(favoriteAccount.getDepositAccountNumber())
                .payeeName(favoriteAccount.getPayeeName())
                .alias(favoriteAccount.getAlias())
                .registeredAt(favoriteAccount.getRegisteredAt())
                .build();
    }

    private FavoriteAccount toDomain(FavoriteAccountJpaEntity entity) {
        return FavoriteAccount.of(entity.getFavoriteAccountId(), entity.getCustomerId(),
                entity.getDepositAccountNumber(), entity.getPayeeName(), entity.getAlias(), entity.getRegisteredAt());
    }
}
