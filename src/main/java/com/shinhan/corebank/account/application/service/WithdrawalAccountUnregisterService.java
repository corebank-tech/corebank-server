package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountUnregisterCommand;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountUnregisterResult;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountUnregisterUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.AutoTransferUsageQueryPort;
import com.shinhan.corebank.account.application.port.out.ScheduledTransferUsageQueryPort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawalAccountUnregisterService
        implements WithdrawalAccountUnregisterUseCase {

    private final AccountPersistencePort accountPersistencePort;

    private final ScheduledTransferUsageQueryPort
            scheduledTransferUsageQueryPort;

    private final AutoTransferUsageQueryPort
            autoTransferUsageQueryPort;

    @Override
    public WithdrawalAccountUnregisterResult unregister(
            WithdrawalAccountUnregisterCommand command
    ) {
        Account account = accountPersistencePort
                .findByAccountIdAndCustomerId(
                        command.accountId(),
                        command.customerId()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                AccountErrorCode
                                        .ACCOUNT_NOT_FOUND_OR_FORBIDDEN
                        )
                );

        // 이미 미등록이면 멱등 성공
        if (!account.isWithdrawalRegistered()) {
            return toResult(account);
        }

        validateNotUsedByScheduledTransfer(
                account.getAccountId()
        );

        validateNotUsedByAutoTransfer(
                account.getAccountId()
        );

        account.unregisterWithdrawalAccount();

        Account savedAccount =
                accountPersistencePort.save(account);

        return toResult(savedAccount);
    }

    private void validateNotUsedByScheduledTransfer(
            Long accountId
    ) {
        if (scheduledTransferUsageQueryPort
                .existsUsingWithdrawalAccount(accountId)) {
            throw new BusinessException(
                    AccountErrorCode
                            .WITHDRAWAL_ACCOUNT_UNREGISTRATION_RESTRICTED
            );
        }
    }

    private void validateNotUsedByAutoTransfer(
            Long accountId
    ) {
        if (autoTransferUsageQueryPort
                .existsUsingWithdrawalAccount(accountId)) {
            throw new BusinessException(
                    AccountErrorCode
                            .WITHDRAWAL_ACCOUNT_UNREGISTRATION_RESTRICTED
            );
        }
    }

    private WithdrawalAccountUnregisterResult toResult(
            Account account
    ) {
        return new WithdrawalAccountUnregisterResult(
                account.getAccountId(),
                account.isWithdrawalRegistered()
        );
    }
}
