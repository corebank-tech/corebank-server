package com.shinhan.corebank.account.application.port.out;

// 비밀번호 변경에 필요한 계좌비밀번호·OTP 인증 토큰을 검증하고 소비한다.
public interface AccountPasswordChangeAuthVerificationPort {

    void verifyAccountPasswordToken(String token, Long customerId, Long accountId);

    void verifyOtpToken(String token, Long customerId, Long accountId);
}
