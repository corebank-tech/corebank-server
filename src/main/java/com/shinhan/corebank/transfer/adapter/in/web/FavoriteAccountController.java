package com.shinhan.corebank.transfer.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountRegisterUseCase;

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
public class FavoriteAccountController {

    private final FavoriteAccountRegisterUseCase registerUseCase;
    private final FavoriteAccountQueryUseCase queryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    // 멱등성 확인 후, 재요청 -> 저장된 응답, 신규 요청 -> 등록 (docs/api_conventions.md §7-3)
    public ResponseEntity<ApiResponse<FavoriteAccountResponse>> register(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody FavoriteAccountRegisterRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return withIdempotency(idempotencyKey, customerId, "POST /transfers/favorite-accounts", fingerprint(request),
                new TypeReference<>() {},
                () -> ApiResponse.success(
                        FavoriteAccountResponse.from(registerUseCase.register(request.toCommand(customerId)))));
    }

    @GetMapping
    public ApiResponse<List<FavoriteAccountResponse>> list() {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return ApiResponse.success(queryUseCase.queryAll(customerId).stream()
                .map(FavoriteAccountResponse::from)
                .toList());
    }

    private <T> ResponseEntity<ApiResponse<T>> withIdempotency(
            String idempotencyKey, Long customerId, String endpoint, Object fingerprint,
            TypeReference<ApiResponse<T>> responseType, Supplier<ApiResponse<T>> action) {
        IdempotencyResult idempotencyResult = idempotencyService.begin(idempotencyKey, customerId, endpoint, toJson(fingerprint));
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
