package com.shinhan.corebank.transfer.application.port.out;

import java.util.List;

import com.shinhan.corebank.transfer.domain.FavoriteAccount;

public interface FavoriteAccountPersistencePort {
    FavoriteAccount save(FavoriteAccount favoriteAccount);
    long countByCustomerId(Long customerId);
    List<FavoriteAccount> findAllByCustomerId(Long customerId);
}
