package com.shinhan.corebank.signup.domain.model;

import java.time.Instant;
import java.util.List;

// 검증을 마친 회원가입 입력 스냅샷을 Redis에 보관한다.
public record TempSignupTokenPayload(
        List<AgreedTerm> agreedTerms,
        String existingBankCustomerId,
        String verifiedBankAccountId,
        String userId,
        String passwordHash,
        String email,
        String phoneNumber,
        Instant validatedAt) {

    public TempSignupTokenPayload {
        agreedTerms = List.copyOf(agreedTerms);
    }
}
