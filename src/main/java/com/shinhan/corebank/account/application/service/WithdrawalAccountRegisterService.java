package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterCommand;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterResult;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.WithdrawalAccountAuthVerificationPort;
import com.shinhan.corebank.account.domain.Account;
import com.shinhan.corebank.account.domain.exception.AccountErrorCode;
import com.shinhan.corebank.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
@Transactional
public class WithdrawalAccountRegisterService
        implements WithdrawalAccountRegisterUseCase {

    private static final ZoneId KOREA_ZONE =
            ZoneId.of("Asia/Seoul");

    private final AccountPersistencePort accountPersistencePort;
    private final WithdrawalAccountAuthVerificationPort
            authVerificationPort;
    private final Clock clock;

    @Override
    public WithdrawalAccountRegisterResult register(
            WithdrawalAccountRegisterCommand command
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

        // 이미 등록돼 있으면 상태를 다시 변경하거나
        // 인증토큰을 소비하지 않고 기존 결과를 반환한다.
        if (account.isWithdrawalRegistered()) {
            return toResult(account);
        }

        // 인증토큰을 소비하기 전에
        // 등록 자체가 가능한 계좌인지 먼저 확인한다.
        account.validateWithdrawalRegistrationAllowed();

        authVerificationPort.verifyAccountPasswordToken(
                command.accountPasswordAuthToken(),
                command.customerId(),
                command.accountId()
        );

        authVerificationPort.verifyOtpToken(
                command.otpAuthToken(),
                command.customerId(),
                command.accountId()
        );

        OffsetDateTime registeredAt =
                OffsetDateTime.ofInstant(
                        clock.instant(),
                        KOREA_ZONE
                );

        account.registerWithdrawalAccount(
                registeredAt.toLocalDateTime()
        );

        Account savedAccount =
                accountPersistencePort.save(account);

        return toResult(savedAccount);
    }

    private WithdrawalAccountRegisterResult toResult(
            Account account
    ) {
        return new WithdrawalAccountRegisterResult(
                account.getAccountId(),
                account.getWithdrawalRegisteredAt()
                        .atZone(KOREA_ZONE)
                        .toOffsetDateTime()
        );
    }
}