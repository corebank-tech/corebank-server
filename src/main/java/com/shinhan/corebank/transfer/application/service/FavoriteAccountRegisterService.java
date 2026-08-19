package com.shinhan.corebank.transfer.application.service;

import java.time.Clock;
import java.time.LocalDateTime;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountRegisterCommand;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountRegisterUseCase;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountResult;
import com.shinhan.corebank.transfer.application.port.out.AccountLockPort;
import com.shinhan.corebank.transfer.application.port.out.FavoriteAccountPersistencePort;
import com.shinhan.corebank.transfer.application.port.out.LockedAccountStatus;
import com.shinhan.corebank.transfer.application.port.out.ResolvedPayee;
import com.shinhan.corebank.transfer.domain.FavoriteAccount;
import com.shinhan.corebank.transfer.domain.exception.FavoriteAccountErrorCode;
import com.shinhan.corebank.transfer.domain.exception.TransferErrorCode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FavoriteAccountRegisterService implements FavoriteAccountRegisterUseCase {

    private static final int MAX_FAVORITE_ACCOUNTS = 20;

    private final AccountLockPort accountLockPort;
    private final FavoriteAccountPersistencePort persistencePort;
    private final Clock clock;

    public FavoriteAccountRegisterService(AccountLockPort accountLockPort, FavoriteAccountPersistencePort persistencePort, Clock clock) {
        this.accountLockPort = accountLockPort;
        this.persistencePort = persistencePort;
        this.clock = clock;
    }

    @Override
    public FavoriteAccountResult register(FavoriteAccountRegisterCommand command) {
        ResolvedPayee payee = accountLockPort.resolvePayeeByAccountNumber(command.depositAccountNumber())
                .orElseThrow(() -> new BusinessException(TransferErrorCode.PAYEE_NOT_FOUND));

        if (persistencePort.countByCustomerId(command.customerId()) >= MAX_FAVORITE_ACCOUNTS) {
            throw new BusinessException(FavoriteAccountErrorCode.MAX_FAVORITE_ACCOUNTS_EXCEEDED);
        }

        FavoriteAccount favoriteAccount = FavoriteAccount.register(
                command.customerId(), command.depositAccountNumber(), payee.payeeName(),
                command.alias(), LocalDateTime.now(clock));

        try {
            FavoriteAccount saved = persistencePort.save(favoriteAccount);
            return FavoriteAccountResult.of(saved, payee.status() == LockedAccountStatus.ACTIVE);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(FavoriteAccountErrorCode.FAVORITE_ACCOUNT_ALREADY_EXISTS);
        }
    }
}
