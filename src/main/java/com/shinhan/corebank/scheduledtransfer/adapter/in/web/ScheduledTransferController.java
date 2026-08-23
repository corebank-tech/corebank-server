package com.shinhan.corebank.scheduledtransfer.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.idempotency.IdempotencyResult;
import com.shinhan.corebank.common.idempotency.IdempotencyService;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.common.response.PageResponse;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultPage;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferExecutionResultSort;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelCommand;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferCancelUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferListItem;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferQueryUseCase;
import com.shinhan.corebank.scheduledtransfer.application.port.in.ScheduledTransferRegisterUseCase;
import com.shinhan.corebank.scheduledtransfer.domain.ScheduledTransferStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "예약이체", description = "예약이체 등록·조회·취소 및 처리결과 조회 API")
public class ScheduledTransferController {
    private final ScheduledTransferRegisterUseCase scheduledTransferRegisterUseCase;
    private final ScheduledTransferQueryUseCase scheduledTransferQueryUseCase;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final CurrentCustomerProvider currentCustomerProvider;
    private final ScheduledTransferCancelUseCase scheduledTransferCancelUseCase;

    @PostMapping
    // 멱등성 확인 후, 재요청 -> 저장된 응답, 신규 요청 -> 등록
    @Operation(operationId = "registerScheduledTransfer", summary = "예약이체 등록", description = """
            지정한 출금계좌에서 미래 특정일에 1회 실행되도록 예약이체를 등록한다. 동일한 Idempotency-Key와 동일한 \
            요청 내용으로 재요청하면 새로 처리하지 않고 저장된 응답을 그대로 반환한다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`SCD0001` 예약일자 범위 오류 · `SCD0005` 이체금액 오류 · `SCD0006` 통장 표시내용 길이 초과 · " +
                            "`SCD0007` 입금 불가 상품유형 · `LMT0002` 1회 이체한도 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`SCD0202` 출금계좌 소유자 아님/비활성 또는 입금계좌를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "`SCD0301` 동일 조건의 예약이체 중복 등록 · `CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<ScheduledTransferResponse>> register(
            @Parameter(description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
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
    @Operation(operationId = "cancelScheduledTransfer", summary = "예약이체 취소", description = """
            대기(WAITING) 상태의 예약이체를 취소한다. 실행 예정일 당일에는 취소할 수 없다. 동일한 Idempotency-Key와 \
            동일한 요청 내용으로 재요청하면 새로 처리하지 않고 저장된 응답을 그대로 반환한다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "취소 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`SCD0201` 예약이체를 찾을 수 없음(본인 소유가 아닌 경우도 동일)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "`SCD0302` 대기 상태가 아닌 예약이체 · `SCD0303` 실행 예정일 당일 취소 시도 · `CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<ScheduledTransferCancelResponse>> cancel(
            @Parameter(description = "취소할 예약이체 ID", required = true, example = "1")
            @PathVariable Long scheduledTransferId,
            @Parameter(description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(description = "계좌 비밀번호 인증 완료 후 발급되는 1회성 인증 토큰", required = true)
            @RequestHeader("Account-Password-Auth-Token") String accountPasswordAuthToken,
            @Parameter(description = "OTP 인증 완료 후 발급되는 1회성 인증 토큰", required = true)
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
    @Operation(operationId = "getScheduledTransfers", summary = "예약이체 목록조회", description = "등록된 예약이체(대기중·실행완료·취소 등) 목록을 상태·출금계좌·조회기간으로 조회한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`CMN0001` status 값이 올바르지 않음 · `CMN0003` 조회 시작일이 종료일보다 늦음 · " +
                            "`CMN0004` 조회기간이 최대 1년(365일) 초과 · `CMN0005` 지원하지 않는 페이지 크기(5/10/20/30/50 중 하나여야 함)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<PageResponse<ScheduledTransferListItemResponse>> search(
            @Parameter(description = "예약이체 상태 필터. 미전달 또는 ALL이면 전체 조회", example = "WAITING")
            @RequestParam(required = false) String status,
            @Parameter(description = "출금계좌 ID (내 계좌). 미전달 시 전체 계좌 대상", example = "1001")
            @RequestParam(required = false) Long withdrawalAccountId,
            @Parameter(description = "조회기간 시작일. fromDate/toDate 둘 다 미전달 시 기간 제한 없이 조회(기본값 없음)", example = "2026-07-20")
            @RequestParam(required = false) LocalDate fromDate,
            @Parameter(description = "조회기간 종료일. fromDate/toDate 둘 다 미전달 시 기간 제한 없이 조회(기본값 없음)", example = "2026-08-20")
            @RequestParam(required = false) LocalDate toDate,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 5/10/20/30/50 중 하나만 허용", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        Page<ScheduledTransferListItem> result = scheduledTransferQueryUseCase.search(
                customerId, parseStatus(status), withdrawalAccountId, fromDate, toDate, page, size);
        return ApiResponse.success(PageResponse.from(result, ScheduledTransferListItemResponse::from));
    }

    // 처리결과 조회
    @GetMapping("/executions")
    @Operation(operationId = "getScheduledTransferExecutions", summary = "예약이체 처리결과 조회", description = """
            등록된 예약이체의 처리결과(정상/오류/취소)를 조회기간별로 조회한다. 조회기간을 지정하지 않으면 \
            최근 1개월(오늘 기준)을 기본값으로 조회한다. 목록 상단에 정상처리금액·오류처리금액·취소건수 집계를 함께 반환한다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`CMN0003` 조회 시작일이 종료일보다 늦음 · `CMN0004` 조회기간이 최대 1년(365일) 초과 · " +
                            "`CMN0005` 지원하지 않는 페이지 크기(5/10/20/30/50 중 하나여야 함)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<ScheduledTransferExecutionResultPageResponse> searchExecutionResults(
            @Parameter(description = "출금계좌 ID (내 계좌). 미전달 시 전체 계좌 대상", example = "1001")
            @RequestParam(required = false) Long withdrawalAccountId,
            @Parameter(description = "조회기간 시작일. 미전달 시 toDate로부터 1개월 전", example = "2026-07-20")
            @RequestParam(required = false) LocalDate fromDate,
            @Parameter(description = "조회기간 종료일. 미전달 시 오늘", example = "2026-08-20")
            @RequestParam(required = false) LocalDate toDate,
            @Parameter(description = "정렬 순서. LATEST(최신순, 기본값)/OLDEST(오래된순)", example = "LATEST")
            @RequestParam(defaultValue = "LATEST") ScheduledTransferExecutionResultSort sort,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 5/10/20/30/50 중 하나만 허용", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        ScheduledTransferExecutionResultPage result = scheduledTransferQueryUseCase.searchExecutionResults(
                customerId, withdrawalAccountId, fromDate, toDate, sort, page, size);
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