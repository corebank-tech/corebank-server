package com.shinhan.corebank.signup.application.port.in;

import java.util.List;

// 애플리케이션 계층에 약관별 동의 정보를 전달한다.
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
