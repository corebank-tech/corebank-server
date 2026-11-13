package com.shinhan.corebank.fds.adapter.in.web;

import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.fds.application.service.FdsAgentTokenVerifier;
import com.shinhan.corebank.fds.application.service.FdsAgentToolService;
import com.shinhan.corebank.fds.application.service.FdsInvestigationReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Python Agent 전용 읽기 Tool과 멱등 보고서 콜백 계약. */
@RestController
@RequestMapping("/internal/fds")
@RequiredArgsConstructor
@Validated
@Tag(name = "FDS Agent 내부 API", description = "FDS_AGENT_SERVICE_TOKEN 전용 Tool·보고서 API")
@SecurityRequirement(name = "bearerAuth")
public class FdsAgentToolController {
    private final FdsAgentTokenVerifier tokens;
    private final FdsAgentToolService tools;
    private final FdsInvestigationReportService reports;

    @GetMapping("/holds/{transferHoldId}/history")
    @Operation(
            operationId = "getFdsAgentTransferHistory",
            summary = "최근 성공 이체 조회",
            description = "FDS_AGENT_SERVICE_TOKEN으로 보류 고객의 heldAt 이전 24시간을 최신순 100건까지 조회합니다.")
    public ApiResponse<FdsAgentToolService.HistoryData> history(
            @PathVariable @Positive long transferHoldId,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        tokens.verify(authorization);
        return ApiResponse.success(tools.history(transferHoldId));
    }

    @GetMapping("/holds/{transferHoldId}/recipient-context")
    @Operation(
            operationId = "getFdsAgentRecipientContext",
            summary = "수취인 성공 이력 요약",
            description = "FDS_AGENT_SERVICE_TOKEN으로 보류 원본의 고객·수취계좌·출금계좌 범위만 조회합니다.")
    public ApiResponse<FdsAgentToolService.RecipientData> recipient(
            @PathVariable @Positive long transferHoldId,
            @RequestHeader(name = "Authorization", required = false) String authorization) {
        tokens.verify(authorization);
        return ApiResponse.success(tools.recipient(transferHoldId));
    }

    @PutMapping(value = "/investigations/{investigationId}/report", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "submitFdsInvestigationReport",
            summary = "조사 보고서 제출",
            description =
                    "FDS_AGENT_SERVICE_TOKEN과 investigationId와 같은 Idempotency-Key가 필요합니다. 실행 요청에서 받은 attemptNo를 본문에 그대로 포함해야 하며 이전 세대의 늦은 보고서는 거부합니다.")
    public ApiResponse<FdsInvestigationReportService.Acknowledgement> report(
            @PathVariable String investigationId,
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @RequestBody String reportJson) {
        tokens.verify(authorization);
        if (!investigationId.equals(idempotencyKey)) {
            throw new com.shinhan.corebank.common.exception.BusinessException(
                    com.shinhan.corebank.fds.domain.exception.FdsErrorCode.FLOW_CONFLICT);
        }
        return ApiResponse.success(reports.submit(investigationId, reportJson));
    }
}
