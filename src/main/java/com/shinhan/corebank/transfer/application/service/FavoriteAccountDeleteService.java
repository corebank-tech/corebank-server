package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountDeleteCommand;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountDeleteUseCase;
import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;
import com.shinhan.corebank.transfer.domain.exception.FavoriteAccountErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FavoriteAccountDeleteService implements FavoriteAccountDeleteUseCase {

    private final FavoriteAccountPersistencePort persistencePort;

    public FavoriteAccountDeleteService(FavoriteAccountPersistencePort persistencePort) {
        this.persistencePort = persistencePort;
    }

    @Override
    public void delete(FavoriteAccountDeleteCommand command) {
        FavoriteAccount favoriteAccount = persistencePort
                .findById(command.favoriteAccountId())
                .filter(fa -> fa.getCustomerId().equals(command.customerId()))
                .orElseThrow(() -> new BusinessException(FavoriteAccountErrorCode.FAVORITE_ACCOUNT_NOT_FOUND));

        persistencePort.deleteById(favoriteAccount.getFavoriteAccountId());
    }
}
