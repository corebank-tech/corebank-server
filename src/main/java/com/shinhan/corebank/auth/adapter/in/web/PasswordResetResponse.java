package com.shinhan.corebank.auth.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record PasswordResetResponse(
        @Schema(description = "비밀번호 변경 일시", example = "2026-09-17T10:00:00+09:00") OffsetDateTime changedAt) {}
