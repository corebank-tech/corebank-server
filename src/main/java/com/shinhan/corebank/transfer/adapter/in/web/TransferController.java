package com.shinhan.corebank.transfer.adapter.in.web;

import java.util.LinkedHashMap;
import java.util.Map;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.application.port.in.PayeeInquiryUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "이체", description = "즉시이체 실행 및 수취인(예금주) 조회 API")
public class TransferController {

    private final TransferExecutionUseCase transferExecutionUseCase;
    private final PayeeInquiryUseCase payeeInquiryUseCase;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final IdempotentRequestExecutor idempotentRequestExecutor;

    @PostMapping
    @Operation(summary = "이체 실행", description = """
            내 계좌에서 상대 계좌로 즉시이체를 실행한다. 동일한 Idempotency-Key로 재요청하면 \
            새로 처리하지 않고 저장된 응답을 그대로 반환한다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이체 요청이 접수됨 (처리 결과는 응답 본문의 status로 구분 — 실행 자체의 성공 여부와 이체 성공 여부는 다르다)")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`TRF0001` 등록되지 않은 출금계좌 · `TRF0002` 출금·입금계좌 동일 · `TRF0003` 이체금액 형식 오류 · `TRF0004` 입금 불가 상품유형 · `TRF0005` 통장 표시내용 길이 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`TRF0201` 입금계좌를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "`TRF0301`/`TRF0304` 거래정지·해지 상태의 입금/출금계좌 · `TRF0303` 출금계좌 잔액 부족 · `CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<TransferResponse>> execute(
            @Parameter(description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(description = "계좌 비밀번호/OTP 인증 완료 후 발급되는 1회성 인증 토큰", required = true)
            @RequestHeader("Account-Password-Auth-Token") String authToken,
            @RequestBody TransferRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return idempotentRequestExecutor.execute(idempotencyKey, customerId, "POST /transfers", fingerprint(customerId, request),
                new TypeReference<>() {},
                () -> ApiResponse.success(TransferResponse.from(
                        transferExecutionUseCase.execute(request.toCommand(customerId, authToken)))));
    }

    @GetMapping("/payee")
    @Operation(summary = "수취인(예금주) 조회", description = "이체 실행 전 입금계좌번호로 예금주명을 조회한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`TRF0201` 입금계좌를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "`TRF0301` 거래정지 또는 해지 상태의 입금계좌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<PayeeResponse> inquirePayee(
            @Parameter(description = "조회할 입금계좌번호 (하이픈 없이)", required = true, example = "11012345678901")
            @RequestParam String accountNumber) {
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
