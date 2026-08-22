package com.shinhan.corebank.customer.application.port.out;

// 신규 이메일의 변경 목적 인증 토큰 검증과 일회성 소비를 추상화한다.
public interface EmailChangeVerificationPort {

    void verifyAndConsume(String emailVerificationToken, String email);
}
