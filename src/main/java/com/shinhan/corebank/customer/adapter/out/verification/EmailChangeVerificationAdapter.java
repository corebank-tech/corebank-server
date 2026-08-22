package com.shinhan.corebank.customer.adapter.out.verification;

import com.shinhan.corebank.customer.application.port.out.EmailChangeVerificationPort;
import com.shinhan.corebank.signup.api.EmailChangeVerificationTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 고객 모듈의 이메일 인증 포트를 signup 공개 verifier에 연결한다.
@Component
@RequiredArgsConstructor
public class EmailChangeVerificationAdapter
        implements EmailChangeVerificationPort {

    private final EmailChangeVerificationTokenVerifier tokenVerifier;

    @Override
    public void verifyAndConsume(
            String emailVerificationToken,
            String email
    ) {
        tokenVerifier.verifyAndConsumeForEmailChange(
                emailVerificationToken,
                email
        );
    }
}
