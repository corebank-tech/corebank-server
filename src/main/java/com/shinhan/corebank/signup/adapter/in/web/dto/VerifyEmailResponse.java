package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.VerifyEmailResult;

import java.time.OffsetDateTime;

// 이메일 인증 완료 토큰과 인증 완료 시각을 반환한다.
public record VerifyEmailResponse(
        String emailVerificationToken,
        OffsetDateTime verifiedAt
) {

    public static VerifyEmailResponse from(VerifyEmailResult result) {
        return new VerifyEmailResponse(
                result.emailVerificationToken(),
                result.verifiedAt()
        );
    }
}
