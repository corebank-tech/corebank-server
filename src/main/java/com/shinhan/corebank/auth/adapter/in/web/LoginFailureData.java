package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;

// 비밀번호 불일치 시 실패 횟수와 잔여 시도 횟수 응답
public record LoginFailureData(
        int errorCount,
        int remainingAttempts
) {

    public static LoginFailureData from(
            LoginAttemptResult attemptResult
    ) {
        return new LoginFailureData(
                attemptResult.errorCount(),
                attemptResult.remainingAttempts()
        );
    }
}
