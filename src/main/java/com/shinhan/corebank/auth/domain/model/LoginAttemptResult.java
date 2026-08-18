package com.shinhan.corebank.auth.domain.model;

// 로그인 실패 횟수와 남은 시도 횟수를 전달하는 결과
public record LoginAttemptResult(
        int errorCount,
        int remainingAttempts
) {
}
