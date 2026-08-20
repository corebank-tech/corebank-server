package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.common.response.PageResponse;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultPage;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferQueryUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterUseCase;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/scheduled-transfers")
@RequiredArgsConstructor
public class ScheduledTransferController {
    private final ScheduledTransferRegisterUseCase scheduledTransferRegisterUseCase;
    private final ScheduledTransferQueryUseCase scheduledTransferQueryUseCase;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final ScheduledTransferCancelUseCase scheduledTransferCancelUseCase;

    @PostMapping
    // 멱등성 확인 후, 재요청 -> 저장된 응답, 신규 요청 -> 등록
    public ResponseEntity<ApiResponse<ScheduledTransferResponse>> register(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                           @RequestBody ScheduledTransferRegisterRequest request,
                                                                           HttpServletRequest httpRequest) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        String requestIp = httpRequest.getRemoteAddr();
        return withIdempotency(idempotencyKey, customerId, "POST /scheduled-transfers", fingerprint(request),
                new TypeReference<>() {},
                () -> ApiResponse.success(ScheduledTransferResponse.from(
                        scheduledTransferRegisterUseCase.register(request.toCommand(requestIp, customerId)))));
    }

    // 취소
    @PostMapping("/{scheduledTransferId}/cancel")
    public ResponseEntity<ApiResponse<ScheduledTransferCancelResponse>> cancel(
            @PathVariable Long scheduledTransferId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("Account-Password-Auth-Token") String accountPasswordAuthToken,
            @RequestHeader("Otp-Auth-Token") String otpAuthToken,
            HttpServletRequest httpRequest) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        String requestIp = httpRequest.getRemoteAddr();
        String endpoint = "POST /scheduled-transfers/" + scheduledTransferId + "/cancel";
        ScheduledTransferCancelCommand command = ScheduledTransferCancelCommand.builder()
                .customerId(customerId)
                .accountPasswordAuthToken(accountPasswordAuthToken)
                .otpAuthToken(otpAuthToken)
                .requestIp(requestIp)
                .build();

        return withIdempotency(idempotencyKey, customerId, endpoint, fingerprint(command),
                new TypeReference<>() {},
                () -> ApiResponse.success(ScheduledTransferCancelResponse.from(
                        scheduledTransferCancelUseCase.cancel(scheduledTransferId, command))));
    }

    // 멱등키 해시는 인증 토큰을 제외한 지문으로 계산 (AutoTransferController.cancel()과 동일 패턴)
    private Map<String, Object> fingerprint(ScheduledTransferCancelCommand command) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("customerId", command.customerId());
        return fingerprint;
    }

    // 조회
    @GetMapping
    public ApiResponse<PageResponse<ScheduledTransferListItemResponse>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long withdrawalAccountId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        Page<ScheduledTransferListItem> result = scheduledTransferQueryUseCase.search(
                customerId, parseStatus(status), withdrawalAccountId, fromDate, toDate, page, size);
        return ApiResponse.success(PageResponse.from(result, ScheduledTransferListItemResponse::from));
    }

    // 처리결과 조회
    @GetMapping("/executions")
    public ApiResponse<ScheduledTransferExecutionResultPageResponse> searchExecutionResults(
            @RequestParam(required = false) Long withdrawalAccountId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        ScheduledTransferExecutionResultPage result = scheduledTransferQueryUseCase.searchExecutionResults(
                customerId, withdrawalAccountId, fromDate, toDate, page, size);
        return ApiResponse.success(ScheduledTransferExecutionResultPageResponse.from(result));
    }

    // ALL은 도메인 Enum에 없는 "조회 조건 전용" 값 — ALL과 미전달 둘 다 "조건 없음"으로 동일하게 처리한다(api_conventions.md §5-10)
    private ScheduledTransferStatus parseStatus(String status) {
        if (status == null || status.equals("ALL")) {
            return null;
        }
        try {
            return ScheduledTransferStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status 값이 올바르지 않습니다.");
        }
    }

    // 멱등키 처리 5단계를 한 곳에 모은 공용 헬퍼 (AutoTransferController와 동일 패턴)
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

    // 멱등키 해시는 인증 토큰을 제외한 지문으로 계산 — 토큰 재발급받아 재시도해도 같은 요청으로 인식되도록
    private Map<String, Object> fingerprint(ScheduledTransferRegisterRequest request) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("withdrawalAccountId", request.withdrawalAccountId());
        fingerprint.put("depositAccountNumber", request.depositAccountNumber());
        fingerprint.put("payeeName", request.payeeName());
        fingerprint.put("amount", request.amount());
        fingerprint.put("scheduledDate", request.scheduledDate());
        fingerprint.put("myPassbookMemo", request.myPassbookMemo());
        fingerprint.put("recipientPassbookMemo", request.recipientPassbookMemo());
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