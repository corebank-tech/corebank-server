package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.IssueEmailVerificationResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 이메일 인증 요청 ID와 Phase 1 인증번호를 반환한다.
public record IssueEmailVerificationResponse(
        @Schema(
                description = "이메일 인증 요청 식별자",
                example = "EMAIL_REQ_7xP9qK2RmY5vLw8Z"
        )
        String emailVerificationId,

        @Schema(
                description = "Phase 1에서 응답하는 이메일 인증번호",
                example = "123456",
                pattern = "^\\d{6}$"
        )
        String verificationCode,

        @Schema(description = "인증번호 남은 유효시간(초)", example = "300")
        long expiresIn
) {

    public static IssueEmailVerificationResponse from(
            IssueEmailVerificationResult result
    ) {
        return new IssueEmailVerificationResponse(
                result.emailVerificationId(),
                result.verificationCode(),
                result.expiresIn()
        );
    }
}
