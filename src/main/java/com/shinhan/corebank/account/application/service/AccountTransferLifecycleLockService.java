package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.api.AccountTransferLifecycleLockUseCase;
import com.shinhan.corebank.account.application.port.out.AccountTransferLifecycleLockPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountTransferLifecycleLockService implements AccountTransferLifecycleLockUseCase {

    private final AccountTransferLifecycleLockPort lockPort;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForRegistration(Long customerId, Long withdrawalAccountId, String depositAccountNumber) {

        lockPort.lockForRegistration(customerId, withdrawalAccountId, depositAccountNumber);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockForEarlyTermination(Long customerId, Long productAccountId, Long destinationAccountId) {

        lockPort.lockForEarlyTermination(customerId, productAccountId, destinationAccountId);
    }
}
