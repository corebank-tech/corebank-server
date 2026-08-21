package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountResult;

// 회원가입 계좌 인증 성공 토큰을 HTTP 응답으로 반환한다.
public record VerifySignupAccountResponse(
        String accountAuthToken,
        long expiresIn
) {

    public static VerifySignupAccountResponse from(
            VerifySignupAccountResult result
    ) {
        return new VerifySignupAccountResponse(
                result.accountAuthToken(),
                result.expiresIn()
        );
    }
}
