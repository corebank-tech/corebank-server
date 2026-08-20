package com.shinhan.corebank.signup.domain.exception;

import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.signup.domain.model.AccountVerificationAttemptResult;

import java.util.Optional;

// 계좌 인증 실패 유형에 따라 시도 횟수의 응답 포함 여부를 제어한다.
public final class AccountVerificationFailedException
        extends BusinessException {

    private final AccountVerificationAttemptResult attemptResult;

    private AccountVerificationFailedException(
            SignupErrorCode errorCode,
            AccountVerificationAttemptResult attemptResult
    ) {
        super(errorCode);
        this.attemptResult = attemptResult;
    }

    public static AccountVerificationFailedException informationMismatch() {
        return new AccountVerificationFailedException(
                SignupErrorCode.ACCOUNT_VERIFICATION_FAILED,
                null
        );
    }

    public static AccountVerificationFailedException passwordMismatch(
            int errorCount,
            int remainingAttempts
    ) {
        return new AccountVerificationFailedException(
                SignupErrorCode.ACCOUNT_VERIFICATION_FAILED,
                new AccountVerificationAttemptResult(
                        errorCount,
                        remainingAttempts
                )
        );
    }

    public static AccountVerificationFailedException locked(
            int errorCount,
            int remainingAttempts
    ) {
        return new AccountVerificationFailedException(
                SignupErrorCode.ACCOUNT_LOCKED,
                new AccountVerificationAttemptResult(
                        errorCount,
                        remainingAttempts
                )
        );
    }

    public Optional<AccountVerificationAttemptResult> getAttemptResult() {
        return Optional.ofNullable(attemptResult);
    }
}
