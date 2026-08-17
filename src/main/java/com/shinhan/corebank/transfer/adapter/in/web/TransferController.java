package com.shinhan.corebank.transfer.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.application.port.in.PayeeInquiryUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.type.TypeReference;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferExecutionUseCase transferExecutionUseCase;
    private final PayeeInquiryUseCase payeeInquiryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping
    public ResponseEntity<ApiResponse<TransferResponse>> execute(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader("Account-Password-Auth-Token") String authToken,
            @RequestBody TransferRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return idempotentRequestExecutor.execute(idempotencyKey, customerId, "POST /transfers", fingerprint(customerId, request),
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
}
