package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.IssuePasswordResetResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record PasswordResetIssueResponse(
        @Schema(description = "비밀번호 재설정 요청 ID", example = "PRR_test") String passwordResetRequestId,
        @Schema(description = "이메일로 발급된 숫자 6자리 인증번호", example = "498210") String verificationCode,
        @Schema(description = "인증번호 유효시간(초)", example = "180") long expiresIn) {
    static PasswordResetIssueResponse from(IssuePasswordResetResult r) {
        return new PasswordResetIssueResponse(r.passwordResetRequestId(), r.verificationCode(), r.expiresIn());
    }
}
