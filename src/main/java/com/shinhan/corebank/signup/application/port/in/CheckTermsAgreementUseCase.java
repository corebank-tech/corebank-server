package com.shinhan.corebank.signup.application.port.in;

public interface CheckTermsAgreementUseCase {

    TermsAgreementResult checkTermsAgreement(
            CheckTermsAgreementCommand command
    );
}