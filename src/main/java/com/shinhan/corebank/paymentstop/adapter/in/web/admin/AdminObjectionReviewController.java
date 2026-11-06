package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ApproveDecisionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ExternalCheckUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase.LedgerEntry;
import com.shinhan.corebank.paymentstop.application.port.in.ReviewProgressUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReviewProgressUseCase.ReviewSession;
import com.shinhan.corebank.paymentstop.application.port.in.ReviewProgressUseCase.SupplementResult;
import com.shinhan.corebank.paymentstop.domain.external.ExternalAgency;
import com.shinhan.corebank.paymentstop.domain.external.ExternalCheckReply;
import com.shinhan.corebank.paymentstop.domain.notice.ObjectionNotice;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

/**
 * 이의제기 심사 진행(PA-10): 소명 완비 확인, 보완 요청, 심사 시작 기록, 2인 승인과 반려, 외부 확인(모의), 통지 기록, 원장 차트. 계약은
 * docs/phase2/objection_admin_contract.md. 승인과 반려는 PAYMENT_STOP_APPROVE, 심사 시작 기록은 PAYMENT_STOP_READ 다(SecurityConfig).
 */
@RestController
@RequestMapping("/admin/objections/{objectionId}")
@RequiredArgsConstructor
@Validated
@Tag(name = "관리자 이의제기 심사 진행", description = "소명 완비 확인, 보완 요청, 2인 승인, 외부 확인, 통지 기록, 원장 차트")
public class AdminObjectionReviewController {

    private final ReviewProgressUseCase reviewProgressUseCase;
    private final ApproveDecisionUseCase approveDecisionUseCase;
    private final ExternalCheckUseCase externalCheckUseCase;
    private final ObjectionQueryUseCase objectionQueryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping("/completeness")
    @Operation(
            operationId = "confirmObjectionCompleteness",
            summary = "소명 완비 확인",
            description = "심사 기한(확인일 + 5영업일)을 건다. 접수(RECEIVED)와 보완 제출(SUPPLEMENT_SUBMITTED) 상태에서만 한다. "
                    + "오류: PST0202 이의제기 없음, PST0308 지금 상태에서 할 수 없음. 멱등키 필수.")
    public ResponseEntity<ApiResponse<CompletenessResponse>> completeness(
            @PathVariable @Positive Long objectionId,
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest http) {
        AdminActor actor = actor(http);
        return execute(
                idempotencyKey,
                actor,
                "/completeness",
                objectionId,
                null,
                () -> new CompletenessResponse(reviewProgressUseCase.confirmCompleteness(objectionId, actor)));
    }

    @PostMapping("/supplement-requests")
    @Operation(
            operationId = "requestObjectionSupplement",
            summary = "보완 요청",
            description = "1회째는 요청일 + 5영업일, 2회째는 + 3영업일이 보완 기한이다. 보완 중에는 심사 기한이 멈추고 보완 뒤 완비를 다시 "
                    + "확인할 때 새로 건다. 명의인 통지를 기록한다. 기한까지 보완이 없으면 목록에 불수용 대상으로 보이고 자동 확정은 없다. "
                    + "오류: PST0006 보완 2회 초과, PST0302 이미 확정, PST0308 지금 상태에서 할 수 없음(보완 대기, 승인 대기). 멱등키 필수.")
    public ResponseEntity<ApiResponse<SupplementResult>> requestSupplement(
            @PathVariable @Positive Long objectionId,
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody SupplementRequestBody request,
            HttpServletRequest http) {
        AdminActor actor = actor(http);
        return execute(
                idempotencyKey,
                actor,
                "/supplement-requests",
                objectionId,
                request,
                () -> reviewProgressUseCase.requestSupplement(
                        objectionId, request.reason(), request.missingDocuments(), actor));
    }

    @PostMapping("/review-sessions")
    @Operation(
            operationId = "startObjectionReviewSession",
            summary = "심사 시작 기록",
            description = "상세 화면을 연 시각을 남긴다. 처리 시간 평가에 쓴다. 조회 권한으로 부른다. 멱등키 필수.")
    public ResponseEntity<ApiResponse<ReviewSession>> startReviewSession(
            @PathVariable @Positive Long objectionId,
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest http) {
        AdminActor actor = actor(http);
        return execute(
                idempotencyKey,
                actor,
                "/review-sessions",
                objectionId,
                null,
                () -> reviewProgressUseCase.startReviewSession(objectionId, actor));
    }

    @PostMapping("/decisions/{decisionId}/approve")
    @Operation(
            operationId = "approveObjectionDecision",
            summary = "판정 승인",
            description = "승인 대기 중인 수용 또는 일부 지급정지 판정을 승인해 확정하고 계좌에 반영한다. 확정 직전 해제 게이트를 다시 계산한다. "
                    + "피해자, 금감원(종료면 명의인, 관련 금융회사까지) 통지를 기록한다. 오류: PST0204 승인할 판정 없음, "
                    + "PST0301 해제 요건 미충족, PST0302 이미 확정, PST0309 작성자와 승인자가 같음. 멱등키 필수.")
    public ResponseEntity<ApiResponse<DecisionResponse>> approve(
            @PathVariable @Positive Long objectionId,
            @PathVariable @Positive Long decisionId,
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest http) {
        AdminActor actor = actor(http);
        return execute(
                idempotencyKey,
                actor,
                "/decisions/" + decisionId + "/approve",
                objectionId,
                null,
                () -> DecisionResponse.from(approveDecisionUseCase.approve(objectionId, decisionId, actor)));
    }

    @PostMapping("/decisions/{decisionId}/return")
    @Operation(
            operationId = "returnObjectionDecision",
            summary = "판정 반려",
            description = "승인 대기 판정을 반려한다. 판정은 RETURNED, 이의제기는 심사 중(UNDER_REVIEW)으로 돌아가 다시 작성한다. "
                    + "오류: PST0204 승인할 판정 없음, PST0302 이미 확정. 멱등키 필수.")
    public ResponseEntity<ApiResponse<DecisionResponse>> returnDecision(
            @PathVariable @Positive Long objectionId,
            @PathVariable @Positive Long decisionId,
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ReturnRequest request,
            HttpServletRequest http) {
        AdminActor actor = actor(http);
        return execute(
                idempotencyKey,
                actor,
                "/decisions/" + decisionId + "/return",
                objectionId,
                request,
                () -> DecisionResponse.from(
                        approveDecisionUseCase.returnDecision(objectionId, decisionId, request.reason(), actor)));
    }

    @PostMapping("/external-checks")
    @Operation(
            operationId = "requestObjectionExternalCheck",
            summary = "외부 확인 요청(모의)",
            description = "요청 즉시 mockReply 로 회신(REPLIED)된다. 같은 기관에 이미 요청했으면 그 행을 그대로 돌려준다. 부정 회신은 "
                    + "불수용 후보, 명의인 무관은 수용 후보가 되고, NO_RECORD 는 게이트에 영향이 없다. 오류: PST0302 이미 확정. 멱등키 필수.")
    public ResponseEntity<ApiResponse<ExternalCheckResponse>> requestExternalCheck(
            @PathVariable @Positive Long objectionId,
            @Parameter(description = "멱등키", required = true) @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ExternalCheckRequest request,
            HttpServletRequest http) {
        AdminActor actor = actor(http);
        return execute(
                idempotencyKey,
                actor,
                "/external-checks",
                objectionId,
                request,
                () -> ExternalCheckResponse.from(
                        externalCheckUseCase.request(objectionId, request.agency(), request.mockReply(), actor)));
    }

    @GetMapping("/external-checks")
    @Operation(operationId = "listObjectionExternalChecks", summary = "외부 확인 목록")
    public ApiResponse<List<ExternalCheckResponse>> externalChecks(@PathVariable @Positive Long objectionId) {
        return ApiResponse.success(externalCheckUseCase.list(objectionId).stream()
                .map(ExternalCheckResponse::from)
                .toList());
    }

    @GetMapping("/notices")
    @Operation(operationId = "listObjectionNotices", summary = "통지 기록", description = "실제로 보내지 않고 기록만 한 통지(D30).")
    public ApiResponse<List<NoticeResponse>> notices(@PathVariable @Positive Long objectionId) {
        return ApiResponse.success(objectionQueryUseCase.notices(objectionId).stream()
                .map(NoticeResponse::from)
                .toList());
    }

    @GetMapping("/ledger")
    @Operation(
            operationId = "listObjectionLedger",
            summary = "원장 타임라인",
            description = "지급정지 계좌의 게이트 창 기간 원장. counterpartyAccountId 는 당행 상대 계좌이고 타행이면 null 이다.")
    public ApiResponse<List<LedgerEntry>> ledger(@PathVariable @Positive Long objectionId) {
        return ApiResponse.success(objectionQueryUseCase.ledger(objectionId));
    }

    // 지문에 대상 이의제기를 넣어, 같은 키로 다른 이의제기를 바꾸면 CMN0302 가 나게 한다
    private <T> ResponseEntity<ApiResponse<T>> execute(
            String idempotencyKey,
            AdminActor actor,
            String path,
            long objectionId,
            Object request,
            Supplier<T> action) {
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                actor.adminCustomerId(),
                "POST /admin/objections/" + objectionId + path,
                IdempotencyFingerprint.of(actor.adminCustomerId(), request, Map.of("objectionId", objectionId)),
                new TypeReference<>() {},
                () -> ApiResponse.success(action.get()));
    }

    private AdminActor actor(HttpServletRequest http) {
        return new AdminActor(currentCustomerProvider.getCurrentCustomerId(), http.getRemoteAddr());
    }

    public record CompletenessResponse(LocalDate reviewDeadline) {}

    public record SupplementRequestBody(
            @NotBlank @Size(max = 500) String reason,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 100) String> missingDocuments) {}

    public record ReturnRequest(@NotBlank @Size(max = 500) String reason) {}

    public record ExternalCheckRequest(@NotNull ExternalAgency agency, @NotNull ExternalCheckReply mockReply) {}

    public record NoticeResponse(
            long noticeId, String target, String kind, String channel, String basis, LocalDateTime recordedAt) {

        static NoticeResponse from(ObjectionNotice notice) {
            return new NoticeResponse(
                    notice.noticeId(),
                    notice.target().name(),
                    notice.kind().name(),
                    notice.channel().name(),
                    notice.basis(),
                    notice.recordedAt());
        }
    }
}
