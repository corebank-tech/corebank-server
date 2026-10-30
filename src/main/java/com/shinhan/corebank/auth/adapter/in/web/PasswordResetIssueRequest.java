package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record PasswordResetIssueRequest(
        @Schema(description = "로그인 아이디", example = "corebank01", requiredMode = Schema.RequiredMode.REQUIRED)
                @Size(min = 5, max = 20)
                String userId,
        @Schema(description = "고객명", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED) @Size(max = 50)
                String customerName,
        @Schema(description = "가입 이메일", example = "user@example.com", requiredMode = Schema.RequiredMode.REQUIRED)
                @Email
                @Size(max = 100)
                String email) {
    IssuePasswordResetCommand toCommand() {
        return new IssuePasswordResetCommand(userId, customerName, email);
    }
}
