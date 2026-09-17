package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetResult;

public record PasswordResetIssueResponse(String passwordResetRequestId, String verificationCode, long expiresIn) {
    static PasswordResetIssueResponse from(IssuePasswordResetResult r) {
        return new PasswordResetIssueResponse(r.passwordResetRequestId(), r.verificationCode(), r.expiresIn());
    }
}
