package com.shinhan.corebank.customer.adapter.in.web;

import com.shinhan.corebank.customer.application.port.in.LoginStatusResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record LoginStatusResponse(
        @Schema(description = "이전 로그인 일시. 이력이 없으면 null", example = "2026-08-21T09:30:00", nullable = true)
                LocalDateTime previousLoginAt,
        @Schema(description = "현재 로그인 요청 IP", example = "203.0.113.10") String currentLoginIp,
        @Schema(description = "최근 거래 일시. 거래 이력이 없으면 null", example = "2026-08-20T14:20:00", nullable = true)
                LocalDateTime lastTransactionAt) {
    public static LoginStatusResponse from(LoginStatusResult result) {
        return new LoginStatusResponse(result.previousLoginAt(), result.currentLoginIp(), result.lastTransactionAt());
    }
}
