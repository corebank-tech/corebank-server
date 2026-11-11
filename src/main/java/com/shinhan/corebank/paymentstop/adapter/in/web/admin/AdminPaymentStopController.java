package com.shinhan.corebank.paymentstop.adapter.in.web.admin;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyFingerprint;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.paymentstop.application.port.in.AdminActor;
import com.shinhan.corebank.paymentstop.application.port.in.RegisterPaymentStopCaseUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

// 지급정지 사건 등록(D-36). 인가는 SecurityConfig 의 /admin/payment-stops 규칙이 맡는다.
@RestController
@RequestMapping("/admin/payment-stops")
@RequiredArgsConstructor
@Tag(name = "관리자 지급정지 사건", description = "피해구제 신청을 받은 계좌의 지급정지 사건 등록")
public class AdminPaymentStopController {

    private final RegisterPaymentStopCaseUseCase registerPaymentStopCaseUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping
    @Operation(
            operationId = "registerPaymentStopCase",
            summary = "지급정지 사건 등록",
            description = "다툼 거래가 그 계좌의 최근 180일 성공 입금인지 확인하고, 사건 등록과 전부 지급정지를 한 트랜잭션에서 한다. "
                    + "오류: PST0001 다툼 거래가 입금이 아님, PST0303 다른 사건에 이미 등록된 거래, "
                    + "ACC0201 계좌 없음, ACC0004 입출금계좌 아님, ACC0301 정상이나 거래정지가 아닌 계좌. "
                    + "멱등키 필수(CMN0002 누락, CMN0301 처리 중, CMN0302 다른 요청에 쓴 키).")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Parameter(description = "멱등키", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest http) {
        long adminCustomerId = currentCustomerProvider.getCurrentCustomerId();
        AdminActor actor = new AdminActor(adminCustomerId, http.getRemoteAddr());

        // 응답 시간 초과 뒤 같은 키로 다시 보내면 처음 만든 사건 ID를 그대로 돌려준다
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                adminCustomerId,
                "POST /admin/payment-stops",
                IdempotencyFingerprint.of(adminCustomerId, request),
                new TypeReference<>() {},
                () -> ApiResponse.success(new RegisterResponse(registerPaymentStopCaseUseCase.register(
                        new RegisterPaymentStopCaseUseCase.Command(
                                request.accountId(), request.disputedTransactionNumbers(), request.objectionDeadline()),
                        actor))));
    }

    public record RegisterRequest(
            @NotNull @Positive Long accountId,
            @NotEmpty @Size(max = 20)
                    List<@NotNull @Pattern(regexp = "^[0-9A-Z]{20}$") String> disputedTransactionNumbers,
            @NotNull LocalDate objectionDeadline) {}

    public record RegisterResponse(long caseId) {}
}
