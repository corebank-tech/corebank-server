package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.domain.ProcessResultStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.idempotency.IdempotentRequestExecutor;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.transfer.application.port.in.PayeeInquiryUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferExecutionUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferHistoryQueryUseCase;
import com.shinhan.corebank.transfer.application.port.in.TransferHistorySort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
    private final TransferHistoryQueryUseCase transferHistoryQueryUseCase;

    @PostMapping
    @Operation(
            operationId = "executeTransfer",
            summary = "이체 실행",
            description =
                    """
            내 계좌에서 상대 계좌로 즉시이체를 실행한다. 동일한 Idempotency-Key와 동일한 요청 내용으로 재요청하면 \
            새로 처리하지 않고 저장된 응답을 그대로 반환한다.""")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description =
                    """
            이체 요청이 처리됨. 실행 자체의 성공 여부와 이체 성공 여부는 다르다 — 응답 본문의 status로 \
            판단한다. status=ERROR면 errorCode에 `TRF0001`(등록되지 않은 출금계좌) · `TRF0002`(출금·입금계좌 동일) \
            · `TRF0004`(입금 불가 상품유형) · `TRF0201`(입금계좌를 찾을 수 없음) · `TRF0301`/`TRF0304`(거래정지·해지 \
            상태의 입금/출금계좌) · `TRF0303`(출금계좌 잔액 부족) · `OTP0101`(OTP 인증 토큰 무효) · `OTP0102`(인증한 \
            거래 내용과 요청 내용 불일치) 중 하나가 담긴다.""")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`TRF0003` 이체금액 형식 오류 · `TRF0005` 통장 표시내용 길이 초과 (요청 파싱 단계에서 즉시 거부됨)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<TransferResponse>> execute(
            @Parameter(
                            description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    @RequestHeader("Idempotency-Key")
                    String idempotencyKey,
            @Parameter(description = "계좌 비밀번호 인증 완료 후 발급되는 1회성 인증 토큰", required = true)
                    @RequestHeader("Account-Password-Auth-Token")
                    String authToken,
            @Parameter(description = "OTP 인증 완료 후 발급되는 1회성 인증 토큰", required = true) @RequestHeader("Otp-Auth-Token")
                    String otpAuthToken,
            @RequestBody TransferRequest request) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return idempotentRequestExecutor.execute(
                idempotencyKey,
                customerId,
                "POST /transfers",
                fingerprint(customerId, request),
                new TypeReference<>() {},
                () -> ApiResponse.success(TransferResponse.from(
                        transferExecutionUseCase.execute(request.toCommand(customerId, authToken, otpAuthToken)))));
    }

    @GetMapping("/payee")
    @Operation(operationId = "getTransferPayee", summary = "수취인(예금주) 조회", description = "이체 실행 전 입금계좌번호로 예금주명을 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`TRF0201` 입금계좌를 찾을 수 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`TRF0301` 거래정지 또는 해지 상태의 입금계좌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<PayeeResponse> inquirePayee(
            @Parameter(description = "조회할 입금계좌번호 (하이픈 없이)", required = true, example = "11012345678901") @RequestParam
                    String accountNumber) {
        return ApiResponse.success(PayeeResponse.from(payeeInquiryUseCase.inquire(accountNumber)));
    }

    @GetMapping
    @Operation(
            operationId = "searchTransfers",
            summary = "이체결과 목록 조회",
            description = "출금계좌 단위로 기간·처리상태·정렬·페이징 조건에 맞는 이체결과 목록과 집계를 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`TRF0001` 등록되지 않은/타인 소유 출금계좌 · `CMN0003`/`CMN0004` 조회기간 오류 · `CMN0005` 잘못된 페이지 크기",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<TransferHistoryPageResponse> search(
            @Parameter(description = "조회할 출금계좌 ID", required = true, example = "101") @RequestParam
                    Long withdrawalAccountId,
            @Parameter(description = "처리상태 필터. SUCCESS/ERROR/PROCESSING, 미지정 또는 ALL이면 전체", example = "SUCCESS")
                    @RequestParam(required = false)
                    String status,
            @Parameter(description = "조회 시작일(미지정 시 종료일-1개월)", example = "2026-08-01") @RequestParam(required = false)
                    LocalDate fromDate,
            @Parameter(description = "조회 종료일(미지정 시 오늘)", example = "2026-08-31") @RequestParam(required = false)
                    LocalDate toDate,
            @RequestParam(defaultValue = "LATEST") TransferHistorySort sort,
            @Parameter(description = "페이지 번호(0부터 시작). all=true면 무시됨", example = "0") @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "페이지 크기. 5/10/20/30/50 중 하나만 허용. all=true면 무시됨", example = "10")
                    @RequestParam(defaultValue = "10")
                    int size,
            @Parameter(description = "true면 페이지 구분 없이 조건에 맞는 전체 건을 반환", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean all) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return ApiResponse.success(TransferHistoryPageResponse.from(transferHistoryQueryUseCase.search(
                customerId, withdrawalAccountId, parseStatus(status), fromDate, toDate, sort, page, size, all)));
    }

    @GetMapping("/{transactionNumber}")
    @Operation(operationId = "getTransferDetail", summary = "이체결과 상세 조회", description = "거래번호로 이체결과 상세를 조회한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`TRF0202` 거래를 찾을 수 없음(타인 소유 거래도 동일하게 처리)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<TransferHistoryDetailResponse> getDetail(
            @Parameter(description = "조회할 거래번호", required = true, example = "20260819IT0000000001") @PathVariable
                    String transactionNumber) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        return ApiResponse.success(TransferHistoryDetailResponse.from(
                transferHistoryQueryUseCase.getDetail(customerId, transactionNumber)));
    }

    // ALL은 도메인 Enum에 없는 "조회 조건 전용" 값 — ALL과 미전달 둘 다 "조건 없음"으로 동일하게 처리한다
    // (AutoTransferController·ScheduledTransferController와 동일 관행, api_conventions.md §5-10)
    private ProcessResultStatus parseStatus(String status) {
        if (status == null || status.equals("ALL")) {
            return null;
        }
        try {
            return ProcessResultStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT, "status 값이 올바르지 않습니다.");
        }
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
