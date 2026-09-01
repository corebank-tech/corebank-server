package com.shinhan.corebank.account.application.service;

import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordCommand;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordResult;
import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordUseCase;
import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenGeneratorPort;
import com.shinhan.corebank.account.application.port.out.AccountPasswordAuthTokenStorePort;
import com.shinhan.corebank.account.config.AccountPasswordProperties;
import com.shinhan.corebank.account.domain.AccountPasswordAttemptResult;
import com.shinhan.corebank.account.domain.AccountPasswordAuthTokenPayload;
import com.shinhan.corebank.account.domain.exception.AccountPasswordErrorCode;
import com.shinhan.corebank.account.domain.exception.AccountPasswordVerificationFailedException;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 계좌비밀번호 검증 결과에 따라 오류를 반환하거나 인증 토큰을 발급한다.
@Service
@RequiredArgsConstructor
public class AccountPasswordVerificationService implements VerifyAccountPasswordUseCase {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("^\\d{4}$");
    private static final int MAX_PASSWORD_ATTEMPTS = 5;

    private final AccountPasswordVerificationProcessor processor;
    private final AccountPasswordAuthTokenGeneratorPort tokenGeneratorPort;
    private final AccountPasswordAuthTokenStorePort tokenStorePort;
    private final AccountPasswordProperties properties;

    @Override
    public VerifyAccountPasswordResult verify(VerifyAccountPasswordCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        validateCommand(command);

        AccountPasswordAttemptResult attempt =
                processor.verify(command.customerId(), command.accountId(), command.accountPassword());

        if (attempt.locked()) {
            throw new AccountPasswordVerificationFailedException(AccountPasswordErrorCode.PASSWORD_LOCKED, attempt);
        }

        if (!attempt.matched()) {
            throw new AccountPasswordVerificationFailedException(AccountPasswordErrorCode.PASSWORD_MISMATCH, attempt);
        }

        String token = tokenGeneratorPort.generate();

        tokenStorePort.save(
                token,
                new AccountPasswordAuthTokenPayload(command.customerId(), command.accountId()),
                properties.authTokenTtl());

        return new VerifyAccountPasswordResult(command.accountId(), true, token, 0, MAX_PASSWORD_ATTEMPTS);
    }

    // 필수값과 숫자 4자리 계좌비밀번호 형식을 확인한다.
    private void validateCommand(VerifyAccountPasswordCommand command) {
        if (command.customerId() == null || command.accountId() == null || command.accountPassword() == null) {
            throw new BusinessException(CommonErrorCode.REQUIRED_FIELD_MISSING);
        }

        if (command.customerId() <= 0
                || command.accountId() <= 0
                || !PASSWORD_PATTERN.matcher(command.accountPassword()).matches()) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
    }
}
