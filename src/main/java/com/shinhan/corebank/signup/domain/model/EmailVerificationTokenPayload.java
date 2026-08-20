package com.shinhan.corebank.signup.domain.model;

import java.time.LocalDateTime;

// 인증된 이메일과 목적을 후속 회원가입 단계에 전달한다.
public record EmailVerificationTokenPayload(
        String email,
        EmailVerificationPurpose purpose,
        LocalDateTime verifiedAt
) {
}
