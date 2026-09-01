package com.shinhan.corebank.otp.application.port.out;

import com.shinhan.corebank.otp.domain.model.OtpVerificationRequest;
import java.time.LocalDateTime;
import java.util.Optional;

// OTP 요청을 기존 verification_request 테이블에 저장하고 잠금 조회한다.
public interface OtpVerificationRequestPort {
    int expireActiveRequests(Long customerId, LocalDateTime now);

    OtpVerificationRequest save(OtpVerificationRequest request);

    Optional<OtpVerificationRequest> findByIdForUpdate(String otpRequestId);

    Optional<OtpVerificationRequest> findVerifiedById(String otpRequestId);
}
