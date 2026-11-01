package com.shinhan.corebank.customer.adapter.in.web.admin;

import com.shinhan.corebank.customer.api.AdminPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record AdminPermissionGrantRequest(
        @Schema(description = "부여할 권한", example = "GL_READ") @NotNull AdminPermission permission) {}
