package com.shinhan.corebank.transfer.application.service;

import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FavoriteAccountQueryService implements FavoriteAccountQueryUseCase {

    private final AccountLockPort accountLockPort;
    private final FavoriteAccountPersistencePort persistencePort;

    public FavoriteAccountQueryService(
            AccountLockPort accountLockPort, FavoriteAccountPersistencePort persistencePort) {
        this.accountLockPort = accountLockPort;
        this.persistencePort = persistencePort;
    }

    @Override
    public List<FavoriteAccountResult> queryAll(Long customerId) {
        List<FavoriteAccount> favoriteAccounts = persistencePort.findAllByCustomerId(customerId);
        Map<String, ResolvedPayee> payeesByAccountNumber =
                accountLockPort.resolvePayeesByAccountNumbers(favoriteAccounts.stream()
                        .map(FavoriteAccount::getDepositAccountNumber)
                        .toList());
        return favoriteAccounts.stream()
                .map(favoriteAccount -> {
                    ResolvedPayee payee = payeesByAccountNumber.get(favoriteAccount.getDepositAccountNumber());
                    boolean transferable = payee != null && payee.status() == LockedAccountStatus.ACTIVE;
                    return FavoriteAccountResult.of(favoriteAccount, transferable);
                })
                .toList();
    }
}
