package com.shinhan.corebank.signup.application.port.out;

import com.shinhan.corebank.signup.domain.model.EmailVerificationPurpose;
import com.shinhan.corebank.signup.domain.model.EmailVerificationRequest;
import java.util.Optional;

// 이메일 인증 요청의 발급·잠금조회·재발급 무효화를 추상화한다.
public interface EmailVerificationRequestPort {

    EmailVerificationRequest save(EmailVerificationRequest request);

    Optional<EmailVerificationRequest> findByIdForUpdate(String requestId);

    int invalidateActive(String email, EmailVerificationPurpose purpose);
}
