package com.shinhan.corebank.account.domain.exception;

import com.shinhan.corebank.account.domain.AccountPasswordAttemptResult;
import com.shinhan.corebank.common.exception.BusinessException;

// 비밀번호 불일치와 잠금 응답에 최신 오류 횟수를 전달한다.
public class AccountPasswordVerificationFailedException
        extends BusinessException {

    private final AccountPasswordAttemptResult attemptResult;

    public AccountPasswordVerificationFailedException(
            AccountPasswordErrorCode errorCode,
            AccountPasswordAttemptResult attemptResult
    ) {
        super(errorCode);
        this.attemptResult = attemptResult;
    }

    public AccountPasswordAttemptResult getAttemptResult() {
        return attemptResult;
    }
}
