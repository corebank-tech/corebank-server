package com.shinhan.corebank.signup.application.port.in;

import java.util.List;

public record CheckTermsAgreementCommand(
        List<Agreement> agreedTerms
) {

    public CheckTermsAgreementCommand {
        agreedTerms = agreedTerms == null
                ? List.of()
                : List.copyOf(agreedTerms);
    }

    public record Agreement(
            String termsId,
            String version,
            boolean isAgreed,
            boolean isRead
    ) {
    }
}
