package com.shinhan.corebank.customer.adapter.in.web.admin;

import com.shinhan.corebank.customer.application.port.in.AdminStatusChangeResult;
import com.shinhan.corebank.customer.domain.model.CustomerStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdminStatusChangeResponse(
        @Schema(description = "대상 고객 내부 식별자", example = "1") Long customerId,
        @Schema(description = "변경 후 계정 상태", example = "SUSPENDED") CustomerStatus status) {

    static AdminStatusChangeResponse from(AdminStatusChangeResult result) {
        return new AdminStatusChangeResponse(result.customerId(), result.status());
    }
}
