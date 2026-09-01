package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.VerifyEmailResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

// 이메일 인증 완료 토큰과 인증 완료 시각을 반환한다.
public record VerifyEmailResponse(
        @Schema(
                        description = "회원가입 입력 검증 또는 이메일 변경에서 한 번 사용할 수 있는 인증 토큰",
                        example = "EMAIL_AUTH_7xP9qK2RmY5vLw8ZbC6dE4fG1hH0jM3n")
                String emailVerificationToken,
        @Schema(description = "이메일 인증 완료 일시", example = "2026-08-22T16:30:00+09:00") OffsetDateTime verifiedAt) {

    public static VerifyEmailResponse from(VerifyEmailResult result) {
        return new VerifyEmailResponse(result.emailVerificationToken(), result.verifiedAt());
    }
}
