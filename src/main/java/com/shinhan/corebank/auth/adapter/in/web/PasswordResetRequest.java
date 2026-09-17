package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.ResetPasswordCommand;

public record PasswordResetRequest(String verificationCode, String newPassword, String newPasswordConfirm) {
    ResetPasswordCommand toCommand(String id) {
        return new ResetPasswordCommand(id, verificationCode, newPassword, newPasswordConfirm);
    }
}
