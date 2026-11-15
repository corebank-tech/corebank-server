package com.shinhan.corebank.fds.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.audit.AuditLogService;
import com.shinhan.corebank.common.audit.PersonalDataAccessEventType;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.fds.application.service.FdsRiskAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 활성 위험 계좌와 관리자 선정 근거를 마스킹된 조회 계약으로 제공한다. */
@RestController
@RequestMapping("/admin/fds/risk-accounts")
@RequiredArgsConstructor
@Validated
@Tag(name = "FDS 위험 계좌", description = "활성 위험 계좌와 관리자 사기 확정 선정 근거")
public class FdsRiskAccountController {
    private final FdsRiskAccountService risks;
    private final CurrentCustomerProvider currentCustomer;
    private final AuditLogService audit;

    /** 활성 데이터셋 및 관리자 사기 확정 위험 계좌 목록을 조회하고 감사 로그를 남긴다. */
    @GetMapping
    @Operation(
            operationId = "findFdsRiskAccounts",
            summary = "활성 위험 계좌 목록",
            description = "CUSTOMER_READ와 X-Access-Reason 헤더가 필요하며 마스킹된 계좌번호와 선정 근거 수를 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "성공: 위험 계좌 목록 반환")
    })
    public ApiResponse<List<FdsRiskAccountService.RiskAccountSummary>> findAll(
            @RequestHeader(name = AccessReason.HEADER) AccessReason accessReason, HttpServletRequest request) {
        List<FdsRiskAccountService.RiskAccountSummary> result = risks.findActiveAccounts();
        audit.recordPersonalDataAccess(
                currentCustomer.getCurrentCustomerId(),
                PersonalDataAccessEventType.FDS_RISK_ACCOUNT_LIST_VIEW,
                request.getRemoteAddr(),
                true,
                Map.of(),
                accessReason);
        return ApiResponse.success(result);
    }

    /** 특정 위험 계좌의 요약 및 사건별 선정 근거 상세를 조회하고 감사 로그를 남긴다. */
    @GetMapping("/{riskAccountId}")
    @Operation(
            operationId = "findFdsRiskAccount",
            summary = "위험 계좌와 사건별 선정 근거 상세",
            description = "CUSTOMER_READ와 X-Access-Reason 헤더가 필요하며 사건별 선정 근거 목록을 반환합니다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "성공: 위험 계좌 상세 및 근거 반환"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "CMN0401 위험 계좌 미존재")
    })
    public ApiResponse<FdsRiskAccountService.RiskAccountDetail> find(
            @PathVariable @Positive long riskAccountId,
            @RequestHeader(name = AccessReason.HEADER) AccessReason accessReason,
            HttpServletRequest request) {
        FdsRiskAccountService.RiskAccountDetail result = risks.findActiveAccount(riskAccountId);
        audit.recordPersonalDataAccess(
                currentCustomer.getCurrentCustomerId(),
                PersonalDataAccessEventType.FDS_RISK_ACCOUNT_DETAIL_VIEW,
                request.getRemoteAddr(),
                true,
                Map.of("riskAccountId", riskAccountId),
                accessReason);
        return ApiResponse.success(result);
    }
}
