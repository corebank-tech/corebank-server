package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.domain.AccountPasswordAttemptResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 계좌비밀번호 실패 응답에 최신 오류 횟수와 잔여 횟수를 포함한다.
public record AccountPasswordFailureData(
        @Schema(description = "계좌 내부 식별자", example = "101")
        Long accountId,

        @Schema(description = "비밀번호 일치 여부", example = "false")
        boolean isMatched,

        @Schema(
                description = "검증 실패 시 발급되지 않는 인증 토큰",
                nullable = true,
                example = "null"
        )
        String accountPasswordAuthToken,

        @Schema(description = "현재 누적 오류 횟수", example = "2")
        int errorCount,

        @Schema(description = "잔여 시도 가능 횟수", example = "3")
        int remainingAttempts
) {

    // 최신 도메인 시도 상태를 계좌비밀번호 실패 데이터로 변환한다.
    public static AccountPasswordFailureData from(
            AccountPasswordAttemptResult result
    ) {
        return new AccountPasswordFailureData(
                result.accountId(),
                false,
                null,
                result.errorCount(),
                result.remainingAttempts()
        );
    }
}
