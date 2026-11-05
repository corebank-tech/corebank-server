package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ConfirmDecisionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReceiveObjectionUseCase;
import com.shinhan.corebank.paymentstop.domain.DecisionResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
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

// 이의제기 접수, 조회, 판정 확정(D-36). 인가는 SecurityConfig 의 /admin/objections 규칙이 맡는다.
@RestController
@RequestMapping("/admin/objections")
@RequiredArgsConstructor
@Validated
@Tag(name = "관리자 이의제기 심사", description = "지급정지 이의제기 접수, 목록, 상세(해제 요건 점검 포함), 판정 확정")
public class AdminObjectionController {

    private final ReceiveObjectionUseCase receiveObjectionUseCase;
    private final ObjectionQueryUseCase objectionQueryUseCase;
    private final ConfirmDecisionUseCase confirmDecisionUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping
    @Operation(
            operationId = "receiveObjection",
            summary = "이의제기 접수",
            description = "사유 조항(법 제7조제1항 1~3호)과 소명을 받는다. 오류: PST0201 사건 없음, PST0002 신청 기한 경과, "
                    + "PST0003 신분증 사본 확인이나 사유 없음, PST0304 이미 접수된 사건. 멱등키 필수.")
    public ResponseEntity<ApiResponse<ReceiveResponse>> receive(
            @Parameter(description = "멱등키", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody ReceiveRequest request,
            HttpServletRequest http) {
        AdminActor actor = actor(http);
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                actor.adminCustomerId(),
                "POST /admin/objections",
                IdempotencyFingerprint.of(actor.adminCustomerId(), request),
                new TypeReference<>() {},
                () -> ApiResponse.success(new ReceiveResponse(receiveObjectionUseCase.receive(
                        new ReceiveObjectionUseCase.Command(
                                request.caseId(),
                                request.clause(),
                                request.statement(),
                                request.simplifiedRequested(),
                                request.idCopyConfirmed(),
                                request.litigationPending()),
                        actor))));
    }

    @GetMapping
    @Operation(operationId = "listObjections", summary = "이의제기 목록", description = "최근 접수 순 200건. 소명 원문은 담지 않는다.")
    public ApiResponse<List<ObjectionSummaryResponse>> list() {
        return ApiResponse.success(objectionQueryUseCase.list().stream()
                .map(ObjectionSummaryResponse::from)
                .toList());
    }

    @GetMapping("/{objectionId}")
    @Operation(
            operationId = "getObjectionDetail",
            summary = "이의제기 상세",
            description = "소명 원문, 다툼 거래, 해제 요건 점검(조회 시점 원장으로 다시 계산), 확정된 판정을 담는다. "
                    + "소명 원문이 보이므로 조회 사유 헤더를 받고 조회 감사를 남긴다(PH-89).")
    public ApiResponse<ObjectionDetailResponse> detail(
            @PathVariable @Positive Long objectionId,
            @Parameter(description = "조회 사유", example = "OPERATION") @RequestHeader(AccessReason.HEADER)
                    AccessReason accessReason,
            HttpServletRequest http) {
        return ApiResponse.success(
                ObjectionDetailResponse.from(objectionQueryUseCase.detail(objectionId, actor(http), accessReason)));
    }

    @PostMapping("/{objectionId}/decisions")
    @Operation(
            operationId = "confirmObjectionDecision",
            summary = "이의제기 판정 확정",
            description = "1인 확정 단계다(2인 승인은 다음 단계). 해제 게이트가 열지 않는 판정은 확정하지 않는다. "
                    + "수용은 지급정지 해제, 일부 지급정지는 다툼 입금액만 남기고 해제, 불수용은 유지다. "
                    + "오류: PST0004 조기 해제 근거 없음, PST0202 이의제기 없음, PST0301 해제 요건 미충족, PST0302 이미 확정. "
                    + "멱등키 필수. 응답 시간 초과 뒤 같은 키로 다시 보내면 처음 확정 결과를 그대로 돌려준다.")
    public ResponseEntity<ApiResponse<DecisionResponse>> confirm(
            @PathVariable @Positive Long objectionId,
            @Parameter(description = "멱등키", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody DecisionRequest request,
            HttpServletRequest http) {
        AdminActor actor = actor(http);
        // 지문에 대상 이의제기를 넣어, 같은 키로 다른 이의제기를 확정하면 CMN0302 가 나게 한다
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                actor.adminCustomerId(),
                "POST /admin/objections/" + objectionId + "/decisions",
                IdempotencyFingerprint.of(actor.adminCustomerId(), request, Map.of("objectionId", objectionId)),
                new TypeReference<>() {},
                () -> ApiResponse.success(DecisionResponse.from(confirmDecisionUseCase.confirm(
                        new ConfirmDecisionUseCase.Command(
                                objectionId, request.result(), request.reason(), request.earlyReleaseBasis()),
                        actor))));
    }

    private AdminActor actor(HttpServletRequest http) {
        return new AdminActor(currentCustomerProvider.getCurrentCustomerId(), http.getRemoteAddr());
    }

    public record ReceiveRequest(
            @NotNull @Positive Long caseId,
            @NotNull @Min(1) @Max(3) Integer clause,
            @NotBlank @Size(max = 4000) String statement,
            boolean simplifiedRequested,
            boolean idCopyConfirmed,
            boolean litigationPending) {}

    public record ReceiveResponse(long objectionId) {}

    public record DecisionRequest(
            @NotNull DecisionResult result,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 500) String earlyReleaseBasis) {}
}
