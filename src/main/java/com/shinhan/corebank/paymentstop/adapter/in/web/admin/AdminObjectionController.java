package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.audit.AccessReason;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.ObjectionQueryUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.ReceiveObjectionUseCase;
import com.shinhan.corebank.paymentstop.application.port.in.WriteDecisionUseCase;
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

// 이의제기 접수, 조회, 판정 작성(D-36). 인가는 SecurityConfig 의 /admin/objections 규칙이 맡는다.
@RestController
@RequestMapping("/admin/objections")
@RequiredArgsConstructor
@Validated
@Tag(name = "관리자 이의제기 심사", description = "지급정지 이의제기 접수, 목록, 상세(해제 요건 점검 포함), 판정 작성")
public class AdminObjectionController {

    private final ReceiveObjectionUseCase receiveObjectionUseCase;
    private final ObjectionQueryUseCase objectionQueryUseCase;
    private final WriteDecisionUseCase writeDecisionUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping
    @Operation(
            operationId = "receiveObjection",
            summary = "이의제기 접수",
            description = "영업점 대리 접수. 사유 조항(법 제7조제1항 1~3호)과 소명을 받는다. 분석을 대기열에 넣고 명의인 접수 통지를 "
                    + "기록한다. 서류는 접수 뒤 서류 업로드로 붙인다. 오류: PST0201 사건 없음이나 해제된 사건, PST0002 신청 기한 경과, "
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
    @Operation(
            operationId = "listObjections",
            summary = "이의제기 목록",
            description = "최근 접수 순 200건. 소명 원문은 담지 않는다. 기한 경보, 보완 기한 경과(불수용 대상), 최근 분석 상태와 " + "마지막 단계 제목을 함께 준다.")
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
            summary = "이의제기 판정 작성",
            description = "불수용은 바로 확정되고 지급정지는 유지된다. 수용과 일부 지급정지는 계좌를 건드리지 않고 승인 대기(PENDING_APPROVAL)로 "
                    + "저장되며, 작성자와 다른 승인자가 승인해야 해제된다. 해제 게이트가 열지 않는 판정은 쓰지 않는다. 수용과 일부 지급정지는 "
                    + "소명 완비 확인 뒤(UNDER_REVIEW)에만 쓴다. 게이트가 닫은 수용과 일부 지급정지는 discretionary=true 와 "
                    + "discretionaryReason(20~1000자)으로 재량 판정을 쓸 수 있다(상세 gate.discretionaryResults). 재량 판정은 승인자가 "
                    + "확인해야 확정된다. 오류: CMN0001 재량 사유 누락이나 길이 위반, 게이트가 이미 연 판정이나 불수용에 재량 표시, "
                    + "PST0004 조기 해제 근거 없음, PST0202 이의제기 없음, PST0301 해제 요건 미충족(재량 표시 없음), "
                    + "PST0302 이미 확정, PST0308 지금 상태에서 쓸 수 없음(승인 대기 중 포함), PST0310 재량으로도 넘을 수 없음"
                    + "(소송 계속, 사유 조항과 판정 범위 불일치, 외부 확인 부정 회신). "
                    + "멱등키 필수. 응답 시간 초과 뒤 같은 키로 다시 보내면 처음 결과를 그대로 돌려준다.")
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
                () -> ApiResponse.success(DecisionResponse.from(writeDecisionUseCase.write(
                        new WriteDecisionUseCase.Command(
                                objectionId,
                                request.result(),
                                request.reason(),
                                request.earlyReleaseBasis(),
                                Boolean.TRUE.equals(request.discretionary()),
                                request.discretionaryReason()),
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

    /**
     * @param discretionary 재량 판정인지. 생략하면 false
     * @param discretionaryReason 재량 사유. discretionary=true 면 20~1000자 필수, 아니면 버린다
     */
    public record DecisionRequest(
            @NotNull DecisionResult result,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 500) String earlyReleaseBasis,
            Boolean discretionary,
            String discretionaryReason) {}
}
