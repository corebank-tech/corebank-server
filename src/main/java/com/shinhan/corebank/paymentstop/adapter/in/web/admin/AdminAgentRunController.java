package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.AgentRunQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.RequestReviewUseCase;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRun;
import com.shinhan.corebank.paymentstop.domain.agent.AgentRunStep;
import com.shinhan.corebank.paymentstop.domain.agent.ReviewDraft;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

// 이의제기 AI 분석 실행 조회와 재분석 요청(D20, D35). 인가는 SecurityConfig 의 /admin/objections 규칙이 맡는다.
@RestController
@RequestMapping("/admin/objections/{objectionId}")
@RequiredArgsConstructor
@Validated
@Tag(name = "관리자 이의제기 AI 분석", description = "AI 분석 실행 목록, 실행 타임라인 폴링, AI 판정안, 재분석 요청")
public class AdminAgentRunController {

    private final AgentRunQueryUseCase agentRunQueryUseCase;
    private final RequestReviewUseCase requestReviewUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @GetMapping("/agent-runs")
    @Operation(
            operationId = "listObjectionAgentRuns",
            summary = "AI 분석 실행 목록",
            description = "최근 실행부터. PST0202 이의제기 없음.")
    public ApiResponse<List<AgentRunResponse>> runs(@PathVariable @Positive Long objectionId) {
        return ApiResponse.success(agentRunQueryUseCase.runs(objectionId).stream()
                .map(AgentRunResponse::from)
                .toList());
    }

    @GetMapping("/agent-runs/{runId}/steps")
    @Operation(
            operationId = "listObjectionAgentRunSteps",
            summary = "AI 분석 실행 타임라인",
            description = "after 보다 큰 순번의 단계만 순서대로 준다. 화면은 1초마다 마지막 순번으로 다시 부르고 실행이 끝나면 멈춘다. " + "PST0203 실행 없음.")
    public ApiResponse<List<AgentRunStepResponse>> steps(
            @PathVariable @Positive Long objectionId,
            @PathVariable @Positive Long runId,
            @Parameter(description = "마지막으로 받은 순번. 처음이면 0", example = "0")
                    @RequestParam(defaultValue = "0")
                    @PositiveOrZero
                    Integer after) {
        return ApiResponse.success(agentRunQueryUseCase.steps(objectionId, runId, after).stream()
                .map(AgentRunStepResponse::from)
                .toList());
    }

    @GetMapping("/review-draft")
    @Operation(
            operationId = "getObjectionReviewDraft",
            summary = "AI 판정안",
            description = "가장 최근 판정안. 없으면 data 가 null 이다. 판정안은 심사 참고안이고 해제 근거가 아니다.")
    public ApiResponse<ReviewDraftResponse> reviewDraft(@PathVariable @Positive Long objectionId) {
        return ApiResponse.success(agentRunQueryUseCase
                .latestDraft(objectionId)
                .map(ReviewDraftResponse::from)
                .orElse(null));
    }

    @PostMapping("/review-requests")
    @Operation(
            operationId = "requestObjectionReview",
            summary = "AI 재분석 요청",
            description = "분석을 대기열에 다시 넣는다. 접수 때는 자동으로 들어가므로 실패했거나 다시 보고 싶을 때만 쓴다. "
                    + "PST0302 이미 판정 확정, PST0306 진행 중인 분석 있음. 멱등키 필수.")
    public ResponseEntity<ApiResponse<ReviewRequestResponse>> requestReview(
            @PathVariable @Positive Long objectionId,
            @Parameter(description = "멱등키", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            HttpServletRequest http) {
        long adminCustomerId = currentCustomerProvider.getCurrentCustomerId();
        AdminActor actor = new AdminActor(adminCustomerId, http.getRemoteAddr());
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                adminCustomerId,
                "POST /admin/objections/" + objectionId + "/review-requests",
                IdempotencyFingerprint.of(adminCustomerId, null, Map.of("objectionId", objectionId)),
                new TypeReference<>() {},
                () -> ApiResponse.success(new ReviewRequestResponse(requestReviewUseCase.request(objectionId, actor))));
    }

    public record ReviewRequestResponse(long runId) {}

    public record AgentRunResponse(
            long runId,
            String status,
            int attempt,
            String model,
            String promptVersion,
            Long inputTokens,
            Long outputTokens,
            Long costMicroUsd,
            LocalDateTime queuedAt,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            String failureReason) {

        static AgentRunResponse from(AgentRun run) {
            return new AgentRunResponse(
                    run.runId(),
                    run.status().name(),
                    run.attempt(),
                    run.model(),
                    run.promptVersion(),
                    run.inputTokens(),
                    run.outputTokens(),
                    run.costMicroUsd(),
                    run.queuedAt(),
                    run.startedAt(),
                    run.endedAt(),
                    run.failureReason());
        }
    }

    public record AgentRunStepResponse(
            int seq,
            String type,
            String title,
            String summary,
            Map<String, Object> detail,
            String status,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {

        static AgentRunStepResponse from(AgentRunStep step) {
            return new AgentRunStepResponse(
                    step.seq(),
                    step.type().name(),
                    step.title(),
                    step.summary(),
                    step.detail(),
                    step.status().name(),
                    step.startedAt(),
                    step.endedAt());
        }
    }

    public record ReviewDraftResponse(
            long runId, String branch, Map<String, Object> content, LocalDateTime submittedAt) {

        static ReviewDraftResponse from(ReviewDraft draft) {
            return new ReviewDraftResponse(draft.runId(), draft.branch().name(), draft.content(), draft.submittedAt());
        }
    }
}
