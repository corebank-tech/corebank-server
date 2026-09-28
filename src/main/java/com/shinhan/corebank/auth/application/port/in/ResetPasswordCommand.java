package com.shinhan.corebank.auth.application.port.in;

public record ResetPasswordCommand(
        String requestId, String verificationCode, String newPassword, String newPasswordConfirm) {}
