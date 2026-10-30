package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.ChangeLoginPasswordResult;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record LoginPasswordChangeResponse(
        @Schema(description = "비밀번호를 변경한 고객의 내부 식별자", example = "1") Long customerId,
        @Schema(description = "비밀번호 변경 일시", example = "2026-09-20T18:30:00+09:00") OffsetDateTime passwordChangedAt) {
    public static LoginPasswordChangeResponse from(ChangeLoginPasswordResult result) {
        return new LoginPasswordChangeResponse(result.customerId(), result.passwordChangedAt());
    }
}
