package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountAliasResult;
import com.shinhan.corebank.account.application.port.in.AccountAliasUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewQueryUseCase;
import com.shinhan.corebank.account.application.port.in.AccountOverviewResult;
import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.type.TypeReference;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
@Validated
@Tag(
        name = "계좌",
        description = "계좌 조회 및 계좌별명 관리 API"
)
public class AccountController {

    private final AccountOverviewQueryUseCase accountOverviewQueryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final AccountAliasUseCase accountAliasUseCase;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @GetMapping
    @Operation(
            summary = "전체 계좌 조회",
            description = "로그인한 고객이 보유한 전체 계좌를 유형별로 그룹화하여 조회한다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "조회 성공"
    )
    public ApiResponse<AccountOverviewResponse> getAccounts() {
        Long customerId =
                currentCustomerProvider.getCurrentCustomerId();

        AccountOverviewResult result =
                accountOverviewQueryUseCase
                        .getOverview(customerId);

        return ApiResponse.success(
                AccountOverviewResponse.from(result)
        );
    }

    @PutMapping("/{accountId}/alias")
    @Operation(
            summary = "계좌별명 등록/수정",
            description = """
                    로그인한 고객이 소유한 계좌의 별명을 등록하거나 수정한다.
                    동일한 Idempotency-Key와 동일한 요청 내용으로 재요청하면
                    새로 처리하지 않고 저장된 응답을 반환한다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "계좌별명 등록/수정 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "`CMN0001` 잘못된 입력값 · `CMN0002` 필수 Idempotency-Key 누락 · `ACC0001` 계좌별명 길이 제한 초과",
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
                    description = "`CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<AccountAliasResponse>>
    changeAlias(
            @Parameter(
                    description = "별명을 등록하거나 수정할 계좌의 내부 식별자",
                    required = true,
                    example = "101"
            )
            @PathVariable
            @Positive
            Long accountId,

            @Parameter(
                    description = "멱등키. 동일 키와 동일 요청 재요청 시 저장된 응답 반환",
                    required = true,
                    example = "550e8400-e29b-41d4-a716-446655440000"
            )
            @RequestHeader("Idempotency-Key")
            String idempotencyKey,

            @RequestBody
            AccountAliasChangeRequest request
    ) {
        Long customerId =
                currentCustomerProvider
                        .getCurrentCustomerId();

        String endpoint =
                "PUT /accounts/"
                        + accountId
                        + "/alias";

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                endpoint,
                changeFingerprint(
                        customerId,
                        accountId,
                        request
                ),
                new TypeReference<>() {
                },
                () -> {
                    AccountAliasResult result =
                            accountAliasUseCase
                                    .changeAlias(
                                            customerId,
                                            accountId,
                                            request.alias()
                                    );

                    return ApiResponse.success(
                            AccountAliasResponse.from(
                                    result
                            )
                    );
                }
        );
    }

    @DeleteMapping("/{accountId}/alias")
    @Operation(
            summary = "계좌별명 삭제",
            description = """
                    로그인한 고객이 소유한 계좌에 설정된 별명을 삭제한다.
                    동일한 Idempotency-Key와 동일한 요청 내용으로 재요청하면
                    새로 처리하지 않고 저장된 응답을 반환한다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "계좌별명 삭제 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "`CMN0001` 잘못된 입력값 · `CMN0002` 필수 Idempotency-Key 누락",
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
                    description = "`CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    public ResponseEntity<ApiResponse<Void>>
    deleteAlias(
            @PathVariable
            @Positive
            Long accountId,

            @RequestHeader("Idempotency-Key")
            String idempotencyKey
    ) {
        Long customerId =
                currentCustomerProvider
                        .getCurrentCustomerId();

        String endpoint =
                "DELETE /accounts/"
                        + accountId
                        + "/alias";

        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                endpoint,
                deleteFingerprint(
                        customerId,
                        accountId
                ),
                new TypeReference<>() {
                },
                () -> {
                    accountAliasUseCase
                            .deleteAlias(
                                    customerId,
                                    accountId
                            );

                    return ApiResponse.success();
                }
        );
    }

    private Map<String, Object> changeFingerprint(
            Long customerId,
            Long accountId,
            AccountAliasChangeRequest request
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

        fingerprint.put(
                "alias",
                request.alias()
        );

        return fingerprint;
    }

    private Map<String, Object> deleteFingerprint(
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