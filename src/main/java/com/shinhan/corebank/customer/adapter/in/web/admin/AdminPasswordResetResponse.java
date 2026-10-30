package com.shinhan.corebank.customer.adapter.in.web.admin;

import com.shinhan.corebank.customer.application.port.in.AdminPasswordResetResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminPasswordResetResponse(
        @Schema(description = "대상 고객 내부 식별자", example = "1") Long customerId,
        @Schema(description = "임시 로그인 비밀번호. 이 응답에서 한 번만 제공되고 서버는 평문을 저장하지 않는다", example = "Ab3!xYz7#Qw9")
                String temporaryPassword,
        @Schema(description = "초기화 후 잠금 여부. 초기화는 잠금도 해제하므로 항상 false", example = "false") boolean accountLocked,
        @Schema(description = "초기화 후 연속 로그인 실패 횟수. 항상 0", example = "0") int loginFailureCount) {

    static AdminPasswordResetResponse from(AdminPasswordResetResult result) {
        return new AdminPasswordResetResponse(
                result.customerId(), result.temporaryPassword(), result.accountLocked(), result.loginFailureCount());
    }

    // 로그에 임시 비밀번호가 찍히지 않게 문자열 표현에서 뺀다.
    @Override
    public String toString() {
        return "AdminPasswordResetResponse[customerId=" + customerId + ", accountLocked=" + accountLocked + "]";
    }
}
