package com.shinhan.corebank.paymentstop.adapter.in.web.internal;

import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.ClaimedJob;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.Context;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.DraftCommand;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.DraftResult;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.LedgerItem;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.OcrCommand;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.StepCommand;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.WorkerDocument;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.WorkerDocumentContent;
import com.shinhan.corebank.paymentstop.application.port.in.AgentWorkerUseCase.WorkerExternalCheck;
import com.shinhan.corebank.paymentstop.domain.agent.AgentStepStatus;
import com.shinhan.corebank.paymentstop.domain.agent.AgentStepType;
import com.shinhan.corebank.paymentstop.domain.document.OcrPage;
import com.shinhan.corebank.paymentstop.domain.document.OcrStatus;
import com.shinhan.corebank.paymentstop.domain.gate.GateBranch;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Agent 워커 전용 내부 API(D15). 계약은 docs/phase2/objection_agent_contract.md. 세션을 쓰지 않는다. 작업 가져가기는 워커
 * 서비스 키(X-Worker-Key)로, 나머지는 작업 토큰(X-Job-Token)으로 인증한다. ALB 에 공개하지 않으며 Swagger 에도 싣지 않는다.
 */
@Hidden
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
@Validated
public class AgentInternalController {

    static final String WORKER_KEY = "X-Worker-Key";
    static final String JOB_TOKEN = "X-Job-Token";

    private final AgentWorkerUseCase agentWorkerUseCase;

    /** 대기열이 비었거나 킬스위치가 꺼졌으면 data 가 null 이다. */
    @PostMapping("/review-jobs/claim")
    public ApiResponse<ClaimedJob> claim(@RequestHeader(value = WORKER_KEY, required = false) String workerKey) {
        return ApiResponse.success(agentWorkerUseCase.claim(workerKey).orElse(null));
    }

    @GetMapping("/objections/{objectionId}/context")
    public ApiResponse<Context> context(
            @RequestHeader(value = JOB_TOKEN, required = false) String jobToken,
            @PathVariable @Positive Long objectionId) {
        return ApiResponse.success(agentWorkerUseCase.context(jobToken, objectionId));
    }

    @GetMapping("/objections/{objectionId}/ledger")
    public ApiResponse<List<LedgerItem>> ledger(
            @RequestHeader(value = JOB_TOKEN, required = false) String jobToken,
            @PathVariable @Positive Long objectionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) @Size(max = 20) String counterparty) {
        return ApiResponse.success(agentWorkerUseCase.ledger(jobToken, objectionId, from, to, counterparty));
    }

    @PostMapping("/agent-runs/{runId}/steps")
    public ApiResponse<Void> recordStep(
            @RequestHeader(value = JOB_TOKEN, required = false) String jobToken,
            @PathVariable @Positive Long runId,
            @Valid @RequestBody StepRequest request) {
        agentWorkerUseCase.recordStep(
                jobToken,
                runId,
                new StepCommand(
                        request.seq(),
                        request.type(),
                        request.title(),
                        request.summary(),
                        request.detail(),
                        request.status(),
                        request.startedAt(),
                        request.endedAt()));
        return ApiResponse.success();
    }

    @PutMapping("/objections/{objectionId}/review-draft")
    public ApiResponse<DraftResult> submitDraft(
            @RequestHeader(value = JOB_TOKEN, required = false) String jobToken,
            @PathVariable @Positive Long objectionId,
            @Valid @RequestBody DraftRequest request) {
        return ApiResponse.success(agentWorkerUseCase.submitDraft(
                jobToken,
                objectionId,
                new DraftCommand(
                        request.branch(),
                        request.evidenceTransactionNumbers(),
                        request.missingDocuments(),
                        request.claims(),
                        request.contradictions(),
                        request.followUps(),
                        request.noticeDraft(),
                        request.model(),
                        request.promptVersion(),
                        request.inputTokens(),
                        request.outputTokens(),
                        request.costMicroUsd())));
    }

    /** 증빙만. 신분증 사본은 목록에 없다(D37). */
    @GetMapping("/objections/{objectionId}/documents")
    public ApiResponse<List<WorkerDocument>> documents(
            @RequestHeader(value = JOB_TOKEN, required = false) String jobToken,
            @PathVariable @Positive Long objectionId) {
        return ApiResponse.success(agentWorkerUseCase.documents(jobToken, objectionId));
    }

    /** 파일 바이트. ApiResponse 로 감싸지 않는다. 신분증 사본은 PST0205 다. */
    @GetMapping("/objections/{objectionId}/documents/{documentId}/content")
    public ResponseEntity<byte[]> documentContent(
            @RequestHeader(value = JOB_TOKEN, required = false) String jobToken,
            @PathVariable @Positive Long objectionId,
            @PathVariable @Positive Long documentId) {
        WorkerDocumentContent content = agentWorkerUseCase.documentContent(jobToken, objectionId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .header("Cache-Control", "no-store")
                .body(content.content());
    }

    /** OCR 원문을 받아 서버가 가명 처리해 저장하고, 저장한 값을 돌려준다. */
    @PutMapping("/objections/{objectionId}/documents/{documentId}/ocr")
    public ApiResponse<OcrResponse> saveOcr(
            @RequestHeader(value = JOB_TOKEN, required = false) String jobToken,
            @PathVariable @Positive Long objectionId,
            @PathVariable @Positive Long documentId,
            @Valid @RequestBody OcrRequest request) {
        List<OcrPage> pages = agentWorkerUseCase.saveOcr(
                jobToken,
                objectionId,
                documentId,
                new OcrCommand(
                        request.engine(),
                        request.status(),
                        request.pages() == null
                                ? List.of()
                                : request.pages().stream()
                                        .map(OcrPageRequest::toPage)
                                        .toList()));
        return ApiResponse.success(new OcrResponse(documentId, pages));
    }

    @GetMapping("/objections/{objectionId}/external-checks")
    public ApiResponse<List<WorkerExternalCheck>> externalChecks(
            @RequestHeader(value = JOB_TOKEN, required = false) String jobToken,
            @PathVariable @Positive Long objectionId) {
        return ApiResponse.success(agentWorkerUseCase.externalChecks(jobToken, objectionId));
    }

    @GetMapping("/objections/{objectionId}/gate")
    public ApiResponse<GateBranchResponse> gate(
            @RequestHeader(value = JOB_TOKEN, required = false) String jobToken,
            @PathVariable @Positive Long objectionId) {
        return ApiResponse.success(new GateBranchResponse(agentWorkerUseCase.gateBranch(jobToken, objectionId)));
    }

    public record GateBranchResponse(GateBranch branch) {}

    public record OcrRequest(
            @Size(max = 40) String engine, OcrStatus status, @Size(max = 50) List<@Valid OcrPageRequest> pages) {}

    public record OcrPageRequest(
            @NotNull @Min(1) Integer page,
            @PositiveOrZero Integer width,
            @PositiveOrZero Integer height,
            @Size(max = 2000) List<@Valid OcrRegionRequest> regions) {

        OcrPage toPage() {
            return new OcrPage(
                    page,
                    width,
                    height,
                    regions == null
                            ? List.of()
                            : regions.stream()
                                    .map(region -> new OcrPage.OcrRegion(
                                            region.index(), region.text(), region.bbox(), region.confidence()))
                                    .toList());
        }
    }

    public record OcrRegionRequest(
            @NotNull @PositiveOrZero Integer index,
            @Size(max = 2000) String text,
            @Size(max = 4) List<@NotNull Double> bbox,
            Double confidence) {}

    public record OcrResponse(long documentId, List<OcrPage> pages) {}

    public record StepRequest(
            @NotNull @Min(1) @Max(10_000) Integer seq,
            @NotNull AgentStepType type,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 500) String summary,
            Map<String, Object> detail,
            @NotNull AgentStepStatus status,
            LocalDateTime startedAt,
            LocalDateTime endedAt) {}

    public record DraftRequest(
            @NotNull GateBranch branch,
            @Size(max = 100) List<@NotBlank @Size(max = 20) String> evidenceTransactionNumbers,
            @Size(max = 50) List<@NotBlank @Size(max = 200) String> missingDocuments,
            @Size(max = 50) List<Map<String, Object>> claims,
            @Size(max = 50) List<Map<String, Object>> contradictions,
            @Size(max = 50) List<@NotBlank @Size(max = 300) String> followUps,
            @Size(max = 2000) String noticeDraft,
            @Size(max = 60) String model,
            @Size(max = 30) String promptVersion,
            @PositiveOrZero Long inputTokens,
            @PositiveOrZero Long outputTokens,
            @PositiveOrZero Long costMicroUsd) {}
}
