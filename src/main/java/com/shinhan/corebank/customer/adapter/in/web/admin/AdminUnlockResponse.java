package com.shinhan.corebank.customer.adapter.in.web.admin;

import com.shinhan.corebank.customer.application.port.in.AdminUnlockResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminUnlockResponse(
        @Schema(description = "대상 고객 내부 식별자", example = "1") Long customerId,
        @Schema(description = "해제 후 잠금 여부. 항상 false", example = "false") boolean accountLocked,
        @Schema(description = "해제 후 연속 로그인 실패 횟수. 항상 0", example = "0") int loginFailureCount) {

    static AdminUnlockResponse from(AdminUnlockResult result) {
        return new AdminUnlockResponse(result.customerId(), result.accountLocked(), result.loginFailureCount());
    }
}
