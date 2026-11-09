package com.shinhan.corebank.fds.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.fds.api.FdsHoldDetail;
import com.shinhan.corebank.fds.application.service.FdsHoldDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자에게 보류 원본과 서버에 저장된 행동 점수를 제공한다. */
@RestController
@RequestMapping("/admin/fds/holds")
@RequiredArgsConstructor
@Validated
@Tag(name = "FDS 관리자", description = "보류 상세 조회")
public class FdsAdminHoldController {
    private final FdsHoldDetailService detail;
    private final CurrentCustomerProvider currentCustomer;

    @GetMapping("/{transferHoldId}")
    @Operation(
            summary = "보류 상세 조회",
            description = "CUSTOMER_READ 권한과 X-Access-Reason이 필요합니다. P6에 저장된 행동 점수만 표시하며 조회 사유를 감사에 남깁니다.")
    public ApiResponse<FdsHoldDetail> get(
            @PathVariable @Positive long transferHoldId,
            @RequestHeader(AccessReason.HEADER) AccessReason accessReason,
            HttpServletRequest request) {
        return ApiResponse.success(detail.get(
                transferHoldId, currentCustomer.getCurrentCustomerId(), request.getRemoteAddr(), accessReason));
    }
}
