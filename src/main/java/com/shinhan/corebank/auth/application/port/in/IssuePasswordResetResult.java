package com.shinhan.corebank.auth.application.port.in;

public record IssuePasswordResetResult(String passwordResetRequestId, String verificationCode, long expiresIn) {}
