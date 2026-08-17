package com.shinhan.corebank.transfer.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.application.port.in.PayeeInquiryUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferExecutionUseCase transferExecutionUseCase;
    private final PayeeInquiryUseCase payeeInquiryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> execute(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("Account-Password-Auth-Token") String authToken,
            @RequestBody TransferRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return withIdempotency(idempotencyKey, customerId, "POST /transfers", fingerprint(customerId, request),
                new TypeReference<>() {},
                () -> ApiResponse.success(TransferResponse.from(
                        transferExecutionUseCase.execute(request.toCommand(customerId, authToken)))));
    }

    @GetMapping("/payee")
    public ApiResponse<PayeeResponse> inquirePayee(@RequestParam String accountNumber) {
        return ApiResponse.success(PayeeResponse.from(payeeInquiryUseCase.inquire(accountNumber)));
    }

    // 멱등키 해시는 인증 토큰을 제외한 지문으로 계산한다(AutoTransferController와 동일 이유) —
    // 인증 토큰을 재발급받아 재시도해도 같은 요청으로 인식되도록
    private Map<String, Object> fingerprint(Long customerId, TransferRequest request) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("customerId", customerId);
        fingerprint.put("withdrawalAccountId", request.withdrawalAccountId());
        fingerprint.put("depositAccountNumber", request.depositAccountNumber());
        fingerprint.put("amount", request.amount());
        fingerprint.put("myPassbookMemo", request.myPassbookMemo());
        fingerprint.put("recipientPassbookMemo", request.recipientPassbookMemo());
        return fingerprint;
    }

    // AutoTransferController.withIdempotency와 동일한 5단계 흐름(시작 → 재생-또는-진행 →
    // 실행 → 완료 → 실패 시 예약 해제)을 그대로 재사용한다.
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
