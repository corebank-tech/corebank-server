package com.shinhan.corebank.otp.domain.model;

// OTP 오답 누적 횟수와 남은 시도 횟수를 전달한다.
public record OtpAttemptResult(int errorCount, int remainingAttempts, boolean locked) {}
