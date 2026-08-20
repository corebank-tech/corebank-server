package com.shinhan.corebank.subscription.application.port.out;

/**
 * subscription 모듈 전용 인증 토큰(계좌비밀번호/OTP) 검증 포트.
 * account.WithdrawalAccountAuthVerificationPort와 이름·시그니처는 같은 결이지만 모듈 간
 * 참조 금지 원칙에 따라 별도 인터페이스다(transfer.TransferAuthTokenVerificationPort도 같은
 * 이유로 자체 포트를 갖고 있다).
 */
public interface ProductSubscriptionAuthTokenVerificationPort {
    void verifyAccountPasswordToken(String token, Long customerId, Long accountId);
    void verifyOtpToken(String token, Long customerId, Long accountId);
}
