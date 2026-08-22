package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.CompleteSignupResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

// 회원가입 완료 결과를 HTTP 응답으로 반환한다.
public record CompleteSignupResponse(
        @Schema(description = "신규 고객 내부 식별자", example = "1")
        Long customerId,

        @Schema(description = "가입 완료된 로그인 아이디", example = "corebank01")
        String userId,

        @Schema(description = "가입 완료 일시", example = "2026-08-22T16:30:00+09:00")
        OffsetDateTime joinedAt
) {
    public static CompleteSignupResponse from(CompleteSignupResult result) {
        return new CompleteSignupResponse(
                result.customerId(), result.userId(), result.joinedAt()
        );
    }
}
