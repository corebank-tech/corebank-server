package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.domain.model.AccountVerificationAttemptResult;

// 계좌비밀번호 검증 실패 횟수를 HTTP 오류 응답에 담는다.
public record AccountVerificationFailureData(
        int errorCount,
        int remainingAttempts
) {

    public static AccountVerificationFailureData from(
            AccountVerificationAttemptResult result
    ) {
        return new AccountVerificationFailureData(
                result.errorCount(),
                result.remainingAttempts()
        );
    }
}
