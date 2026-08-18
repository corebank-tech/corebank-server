package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.autotransfer.application.port.in.*;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.response.PageResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.shinhan.corebank.autotransfer.domain.AutoTransfer;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/auto-transfers")
@RequiredArgsConstructor
public class AutoTransferController {
    private final AutoTransferRegisterUseCase autoTransferRegisterUseCase;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final AutoTransferQueryUseCase autoTransferQueryUseCase;
    private final AutoTransferChangeUseCase autoTransferChangeUseCase;
    private final AutoTransferCancelUseCase autoTransferCancelUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final AutoTransferExecutionHistoryQueryUseCase autoTransferExecutionHistoryQueryUseCase;

    @PostMapping
    // 멱등성 확인 후, 재요청 -> 저장된 응답, 신규 요청 -> 등록
    public ResponseEntity<ApiResponse<AutoTransferResponse>> register (@RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                       @RequestBody AutoTransferRegisterRequest request,
                                                                       HttpServletRequest httpRequest) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        String requestIp = httpRequest.getRemoteAddr();
        return withIdempotency(idempotencyKey, customerId, "POST /auto-transfers", fingerprint(request),
                new TypeReference<>() {},
                () -> ApiResponse.success(AutoTransferResponse.from(
                        autoTransferRegisterUseCase.register(request.toCommand(requestIp, customerId)))));
    }

    //변경
    @PatchMapping("/{autoTransferId}")
    public ResponseEntity<ApiResponse<AutoTransferResponse>> change(@PathVariable Long autoTransferId,
                                                                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                                    @RequestBody AutoTransferChangeRequest request,
                                                                    HttpServletRequest httpRequest) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        String requestIp = httpRequest.getRemoteAddr();
        String endpoint = "PATCH /auto-transfers/" + autoTransferId;
        return withIdempotency(idempotencyKey, customerId, endpoint, fingerprint(request),
                new TypeReference<>() {},
                () -> ApiResponse.success(AutoTransferResponse.from(
                        autoTransferChangeUseCase.change(autoTransferId, request.toCommand(requestIp, customerId)))));
    }

    // 삭제
    @DeleteMapping("/{autoTransferId}")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable Long autoTransferId, @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("Account-Password-Auth-Token") String accountPasswordAuthToken, HttpServletRequest httpRequest) {

        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        String requestIp = httpRequest.getRemoteAddr();
        String endpoint = "DELETE /auto-transfers/" + autoTransferId;
        AutoTransferCancelCommand command = AutoTransferCancelCommand.builder()
                .customerId(customerId)
                .accountPasswordAuthToken(accountPasswordAuthToken)
                .requestIp(requestIp)
                .build();

        return withIdempotency(idempotencyKey, customerId, endpoint, fingerprint(command),
                new TypeReference<>() {},
                () -> {
                    autoTransferCancelUseCase.cancel(autoTransferId, command);
                    return ApiResponse.success();
                });
    }

    // 조회
    @GetMapping
    public ApiResponse<PageResponse<AutoTransferListItemResponse>> search(
            @RequestParam Long withdrawalAccountId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue =  "10") int size) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        Page<AutoTransfer> result = autoTransferQueryUseCase.search(customerId, withdrawalAccountId, parseStatus(status), page, size);
        return ApiResponse.success(PageResponse.from(result, AutoTransferListItemResponse::from));
    }

    // ALL은 도메인 Enum에 없는 "조회 조건 전용" 값 — ALL과 미전달 둘 다 "조건 없음"으로 동일하게 처리한다(api_conventions.md §5-10)
    private AutoTransferStatus parseStatus(String status) {
        if (status == null || status.equals("ALL")) {
            return null;
        }
        try {
            return AutoTransferStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status 값이 올바르지 않습니다.");
        }
    }

    // 멱등키 처리 5단계(시작 → 재생-또는-진행 → 실행 → 완료 → 실패 시 예약 해제)를 한 곳에 모은 공용 헬퍼.
    // register/change/cancel 전부 이 흐름을 그대로 재사용한다.
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
            // action() 자체가 실패했을 때만 예약을 해제한다 — 이미 성공한 뒤(complete() 등)에서 실패하면 여기서 release()를 타면 안 된다.
            // 그러면 이미 커밋된 작업인데 같은 Idempotency-Key로 재시도 시 새로 처리(중복 실행)돼 버린다.
            idempotencyService.release(idempotencyKey);
            throw e;
        }
        idempotencyService.complete(idempotencyKey, (short) HttpStatus.OK.value(), toJson(response));
        return ResponseEntity.ok(response);
    }

    // 멱등키 해시는 인증 토큰을 제외한 지문으로 계산한다(request_hash 컬럼 코멘트 참고) —
    // OTP·계좌비밀번호 인증 토큰을 재발급받아 재시도해도 같은 요청으로 인식되도록
    private Map<String, Object> fingerprint(AutoTransferRegisterRequest request) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("withdrawalAccountId", request.withdrawalAccountId());
        fingerprint.put("depositAccountNumber", request.depositAccountNumber());
        fingerprint.put("payeeName", request.payeeName());
        fingerprint.put("amount", request.amount());
        fingerprint.put("cycleMonths", request.cycleMonths());
        fingerprint.put("transferDay", request.transferDay());
        fingerprint.put("startDate", request.startDate());
        fingerprint.put("endDate", request.endDate());
        fingerprint.put("myPassbookMemo", request.myPassbookMemo());
        fingerprint.put("recipientPassbookMemo", request.recipientPassbookMemo());
        return fingerprint;
    }

    private Map<String, Object> fingerprint(AutoTransferChangeRequest request) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("amount", request.amount());
        fingerprint.put("cycleMonths", request.cycleMonths());
        fingerprint.put("endDate", request.endDate());
        fingerprint.put("myPassbookMemo", request.myPassbookMemo());
        fingerprint.put("recipientPassbookMemo", request.recipientPassbookMemo());
        // 변경 불가 필드도 포함해야 한다 — 빠지면 최초 성공 후 같은 키로 이 필드만 추가한 재요청이
        // fingerprint 동일 판정을 받아 AUT0003 검증 없이 이전 성공 응답을 그대로 재생해버린다
        fingerprint.put("withdrawalAccountId", request.withdrawalAccountId());
        fingerprint.put("depositAccountNumber", request.depositAccountNumber());
        fingerprint.put("transferDay", request.transferDay());
        return fingerprint;
    }

    private Map<String, Object> fingerprint(AutoTransferCancelCommand command) {
        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("customerId", command.customerId());
        return fingerprint;
    }

    // 멱등키 시작·완료 시점에 요청/응답 저장
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("요청/응답을 JSON으로 직렬화하지 못했습니다.", e);
        }
    }

    // 재요청 시 저장된 응답 스냅샷을 그대로 복원
    private <T> T fromJson(String json, TypeReference<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException("저장된 응답을 역직렬화하지 못했습니다.", e);
        }
    }

    // 결과조회
    @GetMapping("/executions")
    public ApiResponse<AutoTransferExecutionHistoryPageResponse> searchExecutionHistory(
            @RequestParam Long withdrawalAccountId,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        AutoTransferExecutionHistoryResult result = autoTransferExecutionHistoryQueryUseCase.search(
                customerId, withdrawalAccountId, fromDate, toDate, page, size);
        return ApiResponse.success(AutoTransferExecutionHistoryPageResponse.from(result));
    }

}
