package com.shinhan.corebank.signup.application.service;

import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountCommand;
import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountResult;
import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountUseCase;
import com.shinhan.corebank.signup.application.port.out.AccountAuthTokenPort;
import com.shinhan.corebank.signup.application.port.out.AuthTokenGeneratorPort;
import com.shinhan.corebank.signup.application.port.out.ExistingBankCustomerVerificationPort;
import com.shinhan.corebank.signup.config.SignupTokenProperties;
import com.shinhan.corebank.signup.domain.exception.AccountVerificationFailedException;
import com.shinhan.corebank.signup.domain.model.AccountAuthTokenPayload;
import com.shinhan.corebank.signup.domain.model.ExistingBankAccountVerification;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

// 기존 은행 계좌를 검증하고 accountAuthToken을 발급한다.
@Service
public class SignupAccountVerificationService
        implements VerifySignupAccountUseCase {

    private final ExistingBankCustomerVerificationPort verificationPort;
    private final AccountAuthTokenPort accountAuthTokenPort;
    private final AuthTokenGeneratorPort authTokenGeneratorPort;
    private final SignupTokenProperties tokenProperties;
    private final Clock clock;

    public SignupAccountVerificationService(
            ExistingBankCustomerVerificationPort verificationPort,
            AccountAuthTokenPort accountAuthTokenPort,
            AuthTokenGeneratorPort authTokenGeneratorPort,
            SignupTokenProperties tokenProperties,
            Clock clock
    ) {
        this.verificationPort = verificationPort;
        this.accountAuthTokenPort = accountAuthTokenPort;
        this.authTokenGeneratorPort = authTokenGeneratorPort;
        this.tokenProperties = tokenProperties;
        this.clock = clock;
    }

    @Override
    public VerifySignupAccountResult verify(
            VerifySignupAccountCommand command
    ) {
        ExistingBankAccountVerification verification =
                verificationPort.verify(
                        command.userName(),
                        command.birthDate(),
                        command.accountNumber(),
                        command.accountPassword()
                );

        return switch (verification.status()) {
            case INFORMATION_MISMATCH ->
                    throw AccountVerificationFailedException
                            .informationMismatch();
            case PASSWORD_MISMATCH ->
                    throw AccountVerificationFailedException
                            .passwordMismatch(
                                    verification.errorCount(),
                                    verification.remainingAttempts()
                            );
            case LOCKED ->
                    throw AccountVerificationFailedException.locked(
                            verification.errorCount(),
                            verification.remainingAttempts()
                    );
            case VERIFIED -> issueToken(verification);
        };
    }

    private VerifySignupAccountResult issueToken(
            ExistingBankAccountVerification verification
    ) {
        String token = authTokenGeneratorPort.generateAccountAuthToken();
        accountAuthTokenPort.save(
                token,
                new AccountAuthTokenPayload(
                        verification.existingBankCustomerId(),
                        verification.existingBankAccountId(),
                        Instant.now(clock)
                ),
                tokenProperties.accountAuthTtl()
        );

        return new VerifySignupAccountResult(
                token,
                tokenProperties.accountAuthTtl().toSeconds()
        );
    }
}
