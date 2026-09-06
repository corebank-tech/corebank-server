package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountDeleteCommand;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountDeleteUseCase;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountRegisterUseCase;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountUpdateUseCase;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final FavoriteAccountUpdateUseCase updateUseCase;
    private final FavoriteAccountDeleteUseCase deleteUseCase;
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

    @PatchMapping("/{favoriteAccountId}")
    // 소유자 검증 실패는 존재하지 않는 항목과 동일한 FAV0201로 응답한다 (타인 소유 항목의 존재 여부 비노출)
    @Operation(
            operationId = "updateFavoriteAccount",
            summary = "자주 쓰는 계좌 별칭 수정",
            description =
                    """
            등록된 자주 쓰는 계좌의 별칭을 수정한다. 동일한 Idempotency-Key와 동일한 요청 내용으로 재요청하면 \
            새로 처리하지 않고 저장된 응답을 그대로 반환한다.""")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0002` 필수 Idempotency-Key 누락 · `FAV0001` 별칭 길이 제한 초과",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`FAV0201` 등록된 계좌를 찾을 수 없음(본인 소유가 아닌 경우도 동일)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<FavoriteAccountResponse>> update(
            @Parameter(description = "수정할 즐겨찾기 계좌 ID", required = true, example = "301") @PathVariable
                    Long favoriteAccountId,
            @Parameter(
                            description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @RequestBody FavoriteAccountUpdateRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return withIdempotency(
                idempotencyKey,
                customerId,
                "PATCH /transfers/favorite-accounts/" + favoriteAccountId,
                updateFingerprint(favoriteAccountId, request),
                new TypeReference<>() {},
                () -> ApiResponse.success(FavoriteAccountResponse.from(
                        updateUseCase.update(request.toCommand(customerId, favoriteAccountId)))));
    }

    @DeleteMapping("/{favoriteAccountId}")
    // 소유자 검증 실패는 존재하지 않는 항목과 동일한 FAV0201로 응답한다 (타인 소유 항목의 존재 여부 비노출)
    @Operation(
            operationId = "deleteFavoriteAccount",
            summary = "자주 쓰는 계좌 삭제",
            description =
                    """
            등록된 자주 쓰는 계좌를 삭제한다. 동일한 Idempotency-Key와 동일한 요청으로 재요청하면 \
            새로 처리하지 않고 저장된 응답을 그대로 반환한다.""")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "삭제 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0002` 필수 Idempotency-Key 누락",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`FAV0201` 등록된 계좌를 찾을 수 없음(본인 소유가 아닌 경우도 동일)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "삭제할 즐겨찾기 계좌 ID", required = true, example = "301") @PathVariable
                    Long favoriteAccountId,
            @Parameter(
                            description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return withIdempotency(
                idempotencyKey,
                customerId,
                "DELETE /transfers/favorite-accounts/" + favoriteAccountId,
                Map.of("favoriteAccountId", favoriteAccountId),
                new TypeReference<>() {},
                () -> {
                    deleteUseCase.delete(new FavoriteAccountDeleteCommand(favoriteAccountId, customerId));
                    return ApiResponse.success();
                });
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

    private Map<String, Object> updateFingerprint(Long favoriteAccountId, FavoriteAccountUpdateRequest request) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("favoriteAccountId", favoriteAccountId);
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
