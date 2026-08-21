package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterCommand;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterResult;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPersistencePort;
import com.shinhan.corebank.account.application.port.out.WithdrawalAccountOtpVerificationPort;
import com.shinhan.corebank.account.application.port.out.WithdrawalAccountPasswordVerificationPort;
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
    private final WithdrawalAccountPasswordVerificationPort
            passwordVerificationPort;
    private final WithdrawalAccountOtpVerificationPort
            otpVerificationPort;
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

        passwordVerificationPort.verifyAccountPasswordToken(
                command.accountPasswordAuthToken(),
                command.customerId(),
                command.accountId()
        );

        otpVerificationPort.verifyAndConsume(
                command.otpAuthToken(),
                command.customerId(),
                command.accountId()
        );

        // 유효한 인증을 거친 재요청이면 상태를 다시 변경하지 않고
        // 기존 등록 결과를 반환한다.
        if (account.isWithdrawalRegistered()) {
            return toResult(account);
        }

        // TODO P6: passwordLocked 시 APW0101 연동

        // 새로 등록하는 경우에만 등록 가능 상태를 검증한다.
        account.validateWithdrawalRegistrationAllowed();

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