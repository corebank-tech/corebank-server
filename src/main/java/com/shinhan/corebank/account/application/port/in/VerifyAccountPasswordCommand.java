package com.shinhan.corebank.account.application.port.in;

// 세션 고객과 대상 계좌 및 입력 비밀번호를 검증 서비스에 전달한다.
public record VerifyAccountPasswordCommand(
        Long customerId,
        Long accountId,
        String accountPassword
) {
}
