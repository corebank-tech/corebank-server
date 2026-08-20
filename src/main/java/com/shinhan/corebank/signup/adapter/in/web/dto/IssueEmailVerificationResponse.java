package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationResult;

// 이메일 인증 요청 ID와 Phase 1 인증번호를 반환한다.
public record IssueEmailVerificationResponse(
        String emailVerificationId,
        String verificationCode,
        long expiresIn
) {

    public static IssueEmailVerificationResponse from(
            IssueEmailVerificationResult result
    ) {
        return new IssueEmailVerificationResponse(
                result.emailVerificationId(),
                result.verificationCode(),
                result.expiresIn()
        );
    }
}
