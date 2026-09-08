package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountResult;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountUpdateCommand;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountUpdateUseCase;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;
import com.shinhan.corebank.transfer.domain.exception.FavoriteAccountErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FavoriteAccountUpdateService implements FavoriteAccountUpdateUseCase {

    private final AccountLockPort accountLockPort;
    private final FavoriteAccountPersistencePort persistencePort;

    public FavoriteAccountUpdateService(
            AccountLockPort accountLockPort, FavoriteAccountPersistencePort persistencePort) {
        this.accountLockPort = accountLockPort;
        this.persistencePort = persistencePort;
    }

    @Override
    public FavoriteAccountResult update(FavoriteAccountUpdateCommand command) {
        FavoriteAccount favoriteAccount = persistencePort
                .findById(command.favoriteAccountId())
                .filter(fa -> fa.getCustomerId().equals(command.customerId()))
                .orElseThrow(() -> new BusinessException(FavoriteAccountErrorCode.FAVORITE_ACCOUNT_NOT_FOUND));

        FavoriteAccount saved = persistencePort.save(favoriteAccount.changeAlias(command.alias()));

        boolean transferable = accountLockPort
                .resolvePayeeByAccountNumber(saved.getDepositAccountNumber())
                .map(payee -> payee.status() == LockedAccountStatus.ACTIVE)
                .orElse(false);
        return FavoriteAccountResult.of(saved, transferable);
    }
}
