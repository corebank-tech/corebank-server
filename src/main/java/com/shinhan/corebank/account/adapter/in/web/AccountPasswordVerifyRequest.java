package com.shinhan.corebank.account.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

// 숫자 4자리의 현재 계좌비밀번호를 입력받는다.
public record AccountPasswordVerifyRequest(
        @Schema(
                description = "숫자 4자리 계좌비밀번호",
                example = "1234"
        )
        String accountPassword
) {
}
