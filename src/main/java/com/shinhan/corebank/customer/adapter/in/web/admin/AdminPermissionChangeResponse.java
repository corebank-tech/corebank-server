package com.shinhan.corebank.customer.adapter.in.web.admin;

import com.shinhan.corebank.customer.api.AdminPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AdminPermissionChangeResponse(
        @Schema(description = "대상 관리자 내부 식별자", example = "1") Long customerId,
        @Schema(description = "변경 후 대상 관리자가 가진 권한 전부. 없으면 빈 배열") List<AdminPermission> permissions) {

    static AdminPermissionChangeResponse from(Long customerId, List<AdminPermission> permissions) {
        return new AdminPermissionChangeResponse(customerId, permissions);
    }
}
