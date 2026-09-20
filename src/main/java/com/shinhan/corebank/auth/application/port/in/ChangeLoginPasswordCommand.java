package com.shinhan.corebank.auth.application.port.in;

public record ChangeLoginPasswordCommand(
        Long customerId,
        String userId,
        String currentPassword,
        String newPassword,
        String newPasswordConfirm,
        String requestIp) {}
