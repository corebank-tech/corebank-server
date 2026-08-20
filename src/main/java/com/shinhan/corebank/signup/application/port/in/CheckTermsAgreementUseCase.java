package com.shinhan.corebank.signup.application.port.in;

// 회원가입 약관 동의 상태를 검증하고 인증 토큰을 발급한다.
public interface CheckTermsAgreementUseCase {

    TermsAgreementResult checkTermsAgreement(
            CheckTermsAgreementCommand command
    );
}
