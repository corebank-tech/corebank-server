package com.shinhan.corebank.signup.domain.model;

import java.time.Instant;
import java.util.List;

// 검증된 약관 동의 목록과 검증 시각을 토큰 값으로 보관한다.
public record TermsAuthTokenPayload(List<AgreedTerm> agreedTerms, Instant issuedAt) {

    public TermsAuthTokenPayload {
        agreedTerms = List.copyOf(agreedTerms);
    }
}
