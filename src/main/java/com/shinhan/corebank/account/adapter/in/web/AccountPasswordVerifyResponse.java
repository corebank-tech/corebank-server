package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.VerifyAccountPasswordResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 계좌비밀번호 검증 성공 결과와 일회용 토큰을 반환한다.
public record AccountPasswordVerifyResponse(
        @Schema(description = "계좌 내부 식별자", example = "101") Long accountId,
        @Schema(description = "비밀번호 일치 여부", example = "true") boolean isMatched,
        @Schema(description = "300초 동안 유효한 일회용 계좌비밀번호 인증 토큰", example = "ACC_PWD_9aB3cF8dE2xY7zL1kM0pN4qR5sT6uV")
                String accountPasswordAuthToken,
        @Schema(description = "현재 누적 오류 횟수", example = "0") int errorCount,
        @Schema(description = "잔여 시도 가능 횟수", example = "5") int remainingAttempts) {

    // application 검증 결과를 성공 응답으로 변환한다.
    public static AccountPasswordVerifyResponse from(VerifyAccountPasswordResult result) {
        return new AccountPasswordVerifyResponse(
                result.accountId(),
                result.matched(),
                result.accountPasswordAuthToken(),
                result.errorCount(),
                result.remainingAttempts());
    }
}
