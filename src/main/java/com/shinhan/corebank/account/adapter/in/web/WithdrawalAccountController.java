package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterResult;
import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterUseCase;
import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/withdrawal-accounts")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "출금계좌",
        description = "출금계좌 관리 API"
)
public class WithdrawalAccountController {

    private static final String REGISTER_SUCCESS_MESSAGE =
            "출금계좌가 등록되었습니다.";

    private final WithdrawalAccountRegisterUseCase
            withdrawalAccountRegisterUseCase;

    private final CurrentCustomerProvider
            currentCustomerProvider;

    private final IdempotentRequestExecutor
            idempotentRequestExecutor;

    @PutMapping("/{accountId}")
    @Operation(
            summary = "출금계좌 등록",
            description = """
                    로그인한 고객이 소유한 입출금계좌를 출금계좌로 등록한다.
                    계좌비밀번호 및 OTP 인증 완료 후 발급된 인증 토큰이 필요하며,
                    동일한 Idempotency-Key와 동일한 요청으로 재요청하면
                    저장된 응답을 반환한다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "출금계좌 등록 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "`CMN0001` 잘못된 입력값 · `CMN0002` 필수 Idempotency-Key 누락 · `ACC0003` 출금계좌 등록 불가 계좌 유형",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "`ACC0201` 계좌를 찾을 수 없거나 접근할 수 없음",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "`ACC0301` 거래정지 또는 해지 상태 · `CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<
            ApiResponse<WithdrawalAccountRegisterResponse>
            > register(
            @PathVariable
            @Positive
            Long accountId,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @Valid
            @RequestBody
            WithdrawalAccountRegisterRequest request
    ) {
        Long customerId =
                currentCustomerProvider
                        .getCurrentCustomerId();

        String endpoint =
                "PUT /withdrawal-accounts/"
                        + accountId;

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                endpoint,
                fingerprint(
                        customerId,
                        accountId
                ),
                new TypeReference<>() {
                },
                () -> {
                    WithdrawalAccountRegisterResult result =
                            withdrawalAccountRegisterUseCase
                                    .register(
                                            request.toCommand(
                                                    customerId,
                                                    accountId
                                            )
                                    );

                    return ApiResponse.success(
                            WithdrawalAccountRegisterResponse
                                    .from(result),
                            REGISTER_SUCCESS_MESSAGE
                    );
                }
        );
    }

    private Map<String, Object> fingerprint(
            Long customerId,
            Long accountId
    ) {
        Map<String, Object> fingerprint =
                new LinkedHashMap<>();

        fingerprint.put(
                "customerId",
                customerId
        );

        fingerprint.put(
                "accountId",
                accountId
        );

        return fingerprint;
    }
}