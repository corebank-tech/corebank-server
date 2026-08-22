package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.domain.model.LoginAttemptResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 비밀번호 불일치 시 실패 횟수와 잔여 시도 횟수 응답
public record LoginFailureData(
        @Schema(description = "로그인 비밀번호 누적 오류 횟수", example = "2")
        int errorCount,

        @Schema(description = "계정 잠금까지 남은 시도 횟수", example = "3")
        int remainingAttempts
) {

    public static LoginFailureData from(
            LoginAttemptResult attemptResult
    ) {
        return new LoginFailureData(
                attemptResult.errorCount(),
                attemptResult.remainingAttempts()
        );
    }
}
