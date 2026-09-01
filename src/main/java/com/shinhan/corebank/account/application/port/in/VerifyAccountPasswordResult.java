package com.shinhan.corebank.account.application.port.in;

// 계좌비밀번호 검증 성공 결과와 발급 토큰을 반환한다.
public record VerifyAccountPasswordResult(
        Long accountId, boolean matched, String accountPasswordAuthToken, int errorCount, int remainingAttempts) {}
