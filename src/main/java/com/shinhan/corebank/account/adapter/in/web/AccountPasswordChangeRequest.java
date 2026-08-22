package com.shinhan.corebank.account.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

// 계좌비밀번호 변경에 필요한 두 인증 토큰과 신규 비밀번호를 입력받는다.
public record AccountPasswordChangeRequest(
        @Schema(
                description = "OTP 검증 성공 후 발급된 일회용 인증 토큰",
                example = "OTP_AUTH_9aB3cF8dE2xY7zL1kM0pN4qR5sT6uV"
        )
        String otpAuthToken,

        @Schema(
                description = "현재 계좌비밀번호 검증 성공 후 발급된 일회용 인증 토큰",
                example = "ACC_PWD_9aB3cF8dE2xY7zL1kM0pN4qR5sT6uV"
        )
        String accountPasswordAuthToken,

        @Schema(
                description = "숫자 4자리 신규 계좌비밀번호",
                example = "5678",
                pattern = "^[0-9]{4}$"
        )
        String newAccountPassword,

        @Schema(
                description = "숫자 4자리 신규 계좌비밀번호 확인값",
                example = "5678",
                pattern = "^[0-9]{4}$"
        )
        String newAccountPasswordConfirm
) {
}
