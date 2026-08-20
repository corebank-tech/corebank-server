package com.shinhan.corebank.signup.domain.model;

import java.time.Instant;

// 계좌 인증 완료 사실을 Redis에 보관할 최소 정보를 표현한다.
public record AccountAuthTokenPayload(
        String existingBankCustomerId,
        String verifiedBankAccountId,
        Instant verifiedAt
) {
}
