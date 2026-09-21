package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record PasswordResetIssueRequest(
        @Size(min = 5, max = 20) String userId,
        @Size(max = 50) String customerName,
        @Email @Size(max = 100) String email) {
    IssuePasswordResetCommand toCommand() {
        return new IssuePasswordResetCommand(userId, customerName, email);
    }
}
