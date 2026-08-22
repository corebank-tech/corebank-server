package com.shinhan.corebank.auth.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

// 로그인 성공 고객정보와 세션 만료시각 응답
public record LoginResponse(
        @Schema(description = "내부 고객 식별자", example = "1")
        Long customerId,

        @Schema(description = "고객명", example = "홍길동")
        String userName,

        @Schema(
                description = "세션 만료 예정 일시",
                example = "2026-08-22T16:10:00+09:00"
        )
        OffsetDateTime sessionExpiresAt
) {
}
