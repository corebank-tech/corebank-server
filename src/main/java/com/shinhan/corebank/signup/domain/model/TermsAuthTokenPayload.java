package com.shinhan.corebank.signup.domain.model;

import java.time.Instant;
import java.util.List;

public record TermsAuthTokenPayload(
        List<AgreedTerm> agreedTerms,
        Instant issuedAt
) {

    public TermsAuthTokenPayload {
        agreedTerms = List.copyOf(agreedTerms);
    }
}
