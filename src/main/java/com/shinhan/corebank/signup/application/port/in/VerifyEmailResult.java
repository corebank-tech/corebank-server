package com.shinhan.corebank.signup.application.port.in;

import java.time.OffsetDateTime;

// 이메일 인증 완료 토큰과 KST 인증 완료 시각을 반환한다.
public record VerifyEmailResult(
        String emailVerificationToken,
        OffsetDateTime verifiedAt
) {
}
