package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.domain.model.AccountVerificationAttemptResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 계좌비밀번호 검증 실패 횟수를 HTTP 오류 응답에 담는다.
public record AccountVerificationFailureData(
        @Schema(description = "계좌비밀번호 누적 오류 횟수", example = "2")
        int errorCount,

        @Schema(description = "계좌 잠금까지 남은 시도 횟수", example = "3")
        int remainingAttempts
) {

    public static AccountVerificationFailureData from(
            AccountVerificationAttemptResult result
    ) {
        return new AccountVerificationFailureData(
                result.errorCount(),
                result.remainingAttempts()
        );
    }
}
