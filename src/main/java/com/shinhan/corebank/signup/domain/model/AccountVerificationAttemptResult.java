package com.shinhan.corebank.signup.domain.model;

// 계좌비밀번호 실패 횟수와 남은 시도 횟수를 표현한다.
public record AccountVerificationAttemptResult(int errorCount, int remainingAttempts) {}
