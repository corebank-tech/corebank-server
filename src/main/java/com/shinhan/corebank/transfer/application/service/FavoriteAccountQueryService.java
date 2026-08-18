package com.shinhan.corebank.transfer.application.service;

import java.util.List;

import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FavoriteAccountQueryService implements FavoriteAccountQueryUseCase {

    private final AccountLockPort accountLockPort;
    private final FavoriteAccountPersistencePort persistencePort;

    public FavoriteAccountQueryService(AccountLockPort accountLockPort, FavoriteAccountPersistencePort persistencePort) {
        this.accountLockPort = accountLockPort;
        this.persistencePort = persistencePort;
    }

    @Override
    public List<FavoriteAccountResult> queryAll(Long customerId) {
        return persistencePort.findAllByCustomerId(customerId).stream()
                .map(this::toResult)
                .toList();
    }

    private FavoriteAccountResult toResult(FavoriteAccount favoriteAccount) {
        boolean transferable = accountLockPort.resolvePayeeByAccountNumber(favoriteAccount.getDepositAccountNumber())
                .map(payee -> payee.status() == LockedAccountStatus.ACTIVE)
                .orElse(false);
        return new FavoriteAccountResult(favoriteAccount.getFavoriteAccountId(), favoriteAccount.getAlias(),
                favoriteAccount.getDepositAccountNumber(), favoriteAccount.getPayeeName(), transferable);
    }
}
