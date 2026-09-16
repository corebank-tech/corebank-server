package com.shinhan.corebank.transfer.application.port.out;

import com.shinhan.corebank.transfer.domain.FavoriteAccount;
import java.util.List;
import java.util.Optional;

public interface FavoriteAccountPersistencePort {
    FavoriteAccount save(FavoriteAccount favoriteAccount);

    long countByCustomerId(Long customerId);

    List<FavoriteAccount> findAllByCustomerId(Long customerId);

    Optional<FavoriteAccount> findById(Long favoriteAccountId);

    void deleteById(Long favoriteAccountId);
}
