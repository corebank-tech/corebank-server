package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountRegisterUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/transfers/favorite-accounts")
@RequiredArgsConstructor
@Tag(name = "자주 쓰는 계좌", description = "즐겨찾기 계좌 등록 및 목록조회 API")
public class FavoriteAccountController {

    private final FavoriteAccountRegisterUseCase registerUseCase;
    private final FavoriteAccountQueryUseCase queryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    // 멱등성 확인 후, 재요청 -> 저장된 응답, 신규 요청 -> 등록 (docs/api_conventions.md §7-3)
    @Operation(
            operationId = "registerFavoriteAccount",
            summary = "자주 쓰는 계좌 등록",
            description =
                    """
            입금계좌번호를 자주 쓰는 계좌로 등록한다. 동일한 Idempotency-Key와 동일한 요청 내용으로 재요청하면 \
            새로 등록하지 않고 저장된 응답을 그대로 반환한다.""")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0002` 필수 Idempotency-Key 누락 · `FAV0001` 별칭 길이 제한 초과",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`TRF0201` 입금계좌를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`FAV0301` 이미 등록된 계좌 · `FAV0302` 최대 20건 초과 · `CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<FavoriteAccountResponse>> register(
            @Parameter(
                            description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody FavoriteAccountRegisterRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return withIdempotency(
                idempotencyKey,
                customerId,
                "POST /transfers/favorite-accounts",
                fingerprint(request),
                new TypeReference<>() {},
                () -> ApiResponse.success(
                        FavoriteAccountResponse.from(registerUseCase.register(request.toCommand(customerId)))));
    }

    @GetMapping
    @Operation(
            operationId = "getFavoriteAccounts",
            summary = "자주 쓰는 계좌 목록조회",
            description = "내가 등록한 자주 쓰는 계좌 목록을 조회한다. 등록 건이 없으면 빈 배열을 반환한다.")
    public ApiResponse<List<FavoriteAccountResponse>> list() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return ApiResponse.success(queryUseCase.queryAll(customerId).stream()
                .map(FavoriteAccountResponse::from)
                .toList());
    }

    private <T> ResponseEntity<ApiResponse<T>> withIdempotency(
            String idempotencyKey,
            Long customerId,
            String endpoint,
            Object fingerprint,
            TypeReference<ApiResponse<T>> responseType,
            Supplier<ApiResponse<T>> action) {
        IdempotencyResult idempotencyResult =
                idempotencyService.begin(idempotencyKey, customerId, endpoint, toJson(fingerprint));
        if (idempotencyResult.replay()) {
            return ResponseEntity.status(idempotencyResult.httpStatus())
                    .body(fromJson(idempotencyResult.responseSnapshot(), responseType));
        }
        ApiResponse<T> response;
        try {
            response = action.get();
        } catch (RuntimeException e) {
            idempotencyService.release(idempotencyKey);
            throw e;
        }
        idempotencyService.complete(idempotencyKey, (short) HttpStatus.OK.value(), toJson(response));
        return ResponseEntity.ok(response);
    }

    private Map<String, Object> fingerprint(FavoriteAccountRegisterRequest request) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("depositAccountNumber", request.depositAccountNumber());
        fingerprint.put("alias", request.alias());
        return fingerprint;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("요청/응답을 JSON으로 직렬화하지 못했습니다.", e);
        }
    }

    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 응답을 역직렬화하지 못했습니다.", e);
        }
    }
}
