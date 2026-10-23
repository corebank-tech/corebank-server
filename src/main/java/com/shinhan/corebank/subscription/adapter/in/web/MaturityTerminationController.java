package com.shinhan.corebank.subscription.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.subscription.api.MaturityTerminationCommand;
import com.shinhan.corebank.subscription.api.MaturityTerminationResult;
import com.shinhan.corebank.subscription.api.MaturityTerminationUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

@Tag(name = "만기해지", description = "예·적금 만기해지 및 원리금 지급 API")
@Validated
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class MaturityTerminationController {

    private final MaturityTerminationUseCase maturityTerminationUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping("/{accountId}/maturity-termination")
    @Operation(
            operationId = "terminateMaturedAccount",
            summary = "만기해지 실행",
            description =
                    """
                    만기가 도래한 본인 예·적금 계좌를 해지한다. 수취 계좌는 가입 시 등록한
                    계좌를 서버에서 조회하므로 요청 본문으로 수취 계좌나 고객 ID를 받지 않는다.
                    원리금 이체·이자 지급·계좌 종료는 동일 트랜잭션에서 처리한다.
                    동일한 Idempotency-Key와 동일 요청으로 재시도하면 최초 성공 응답을 반환한다.
                    계좌비밀번호 검증 API에서 발급받은 일회용 인증 토큰을 Account-Password-Auth-Token 헤더로 전달해야 한다.
                    RENEW(자동 재예치) 방식은 현재 지원하지 않는다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "만기해지 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 계좌 ID 또는 멱등키 형식 오류 · "
                        + "`CMN0002` Idempotency-Key 또는 Account-Password-Auth-Token 헤더 누락",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`APW0101` 계좌비밀번호 잠금 · " + "`APW0102` 계좌비밀번호 인증 토큰 무효·만료·재사용 또는 계좌 불일치",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`ACC0201` 계좌를 찾을 수 없거나 소유권이 없음 · `PRD0203` 가입 내역 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`PRD0302` 이자 중복 지급 · `PRD0303` 가입 처리 미완료 · "
                        + "`PRD0304` 지급 원리금 0원 · `PRD0305` 자동 재예치 미지원 · "
                        + "`ACC0301` 계좌 상태 불일치 · `ACC0305` 만기해지 미지원 계좌 · "
                        + "`ACC0306` 실제 만기일 미도래 · `ACC0307` 잔액 남음 · "
                        + "`CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<MaturityTerminationResponse>> terminate(
            @Parameter(description = "해지할 예·적금 계좌 ID", required = true, example = "101") @PathVariable @Positive
                    Long accountId,
            @Parameter(
                            description = "UUID v4 멱등키. 동일 키와 동일 요청은 최초 응답을 재생",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Parameter(description = "계좌비밀번호 인증 토큰", required = true) @RequestHeader("Account-Password-Auth-Token")
                    String accountPasswordAuthToken) {

        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        String endpoint = "POST /accounts/" + accountId + "/maturity-termination";

        return idempotentRequestExecutor.execute(
                idempotencyKey, customerId, endpoint, Map.of("accountId", accountId), new TypeReference<>() {}, () -> {
                    MaturityTerminationResult result = maturityTerminationUseCase.terminate(
                            new MaturityTerminationCommand(customerId, accountId, accountPasswordAuthToken));
                    return ApiResponse.success(MaturityTerminationResponse.from(result), "만기해지가 완료되었습니다.");
                });
    }
}
