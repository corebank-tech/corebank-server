package com.shinhan.corebank.fds.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.fds.api.FdsHoldDetail;
import com.shinhan.corebank.fds.api.FdsHoldListPage;
import com.shinhan.corebank.fds.application.service.FdsHoldDetailService;
import com.shinhan.corebank.fds.application.service.FdsHoldListService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자에게 보류 원본과 서버에 저장된 행동 점수를 제공한다. */
@RestController
@RequestMapping("/admin/fds/holds")
@RequiredArgsConstructor
@Validated
@Tag(name = "FDS 관리자", description = "보류 상세 조회")
public class FdsAdminHoldController {
    private final FdsHoldDetailService detail;
    private final FdsHoldListService list;
    private final CurrentCustomerProvider currentCustomer;

    @GetMapping("/{transferHoldId}")
    @Operation(
            operationId = "getFdsHeldTransferDetail",
            summary = "보류 상세 조회",
            description =
                    "CUSTOMER_READ 권한과 X-Access-Reason이 필요합니다. 기존 최상위 필드와 확장 상세 객체를 함께 반환하며 Agent가 실제 인용한 Tool 이력만 usedAsEvidence=true로 표시합니다.")
    public ApiResponse<FdsHoldDetail> get(
            @PathVariable @Positive long transferHoldId,
            @RequestHeader(AccessReason.HEADER) AccessReason accessReason,
            HttpServletRequest request) {
        return ApiResponse.success(detail.get(
                transferHoldId, currentCustomer.getCurrentCustomerId(), request.getRemoteAddr(), accessReason));
    }

    @GetMapping
    @Operation(
            operationId = "getFdsHeldTransfers",
            summary = "FDS 보류 거래 목록",
            description =
                    "CUSTOMER_READ 권한과 X-Access-Reason이 필요합니다. heldAt·transferHoldId 내림차순이며 page는 0 이상, size는 1~100(기본 20)입니다.")
    public ApiResponse<FdsHoldListPage> list(
            @RequestParam(defaultValue = "0") @jakarta.validation.constraints.Min(0) int page,
            @RequestParam(defaultValue = "20")
                    @jakarta.validation.constraints.Min(1)
                    @jakarta.validation.constraints.Max(100)
                    int size,
            @RequestParam(required = false) String holdStatus,
            @RequestParam(required = false) String policyCode,
            @RequestParam(required = false) String riskStatus,
            @RequestParam(required = false) String investigationStatus,
            @RequestHeader(AccessReason.HEADER) AccessReason accessReason,
            HttpServletRequest request) {
        return ApiResponse.success(list.find(
                page,
                size,
                holdStatus,
                policyCode,
                riskStatus,
                investigationStatus,
                currentCustomer.getCurrentCustomerId(),
                request.getRemoteAddr(),
                accessReason));
    }
}
