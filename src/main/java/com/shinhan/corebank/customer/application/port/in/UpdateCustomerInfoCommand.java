package com.shinhan.corebank.customer.application.port.in;

// 로그인 고객이 변경할 연락처와 이메일 인증 토큰을 전달한다.
public record UpdateCustomerInfoCommand(
        Long customerId,
        String phoneNumber,
        String email,
        String emailVerificationToken
) {
}
