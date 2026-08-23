package com.shinhan.corebank.autotransfer.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.CurrentCustomerProvider;
import com.shinhan.corebank.autotransfer.application.port.in.*;
import com.shinhan.corebank.autotransfer.domain.AutoTransferStatus;
import com.shinhan.corebank.common.exception.BusinessException;
import com.shinhan.corebank.common.exception.CommonErrorCode;
import com.shinhan.corebank.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
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
@Tag(name = "자동이체", description = "자동이체 등록·조회·변경·해지 및 처리결과 조회 API")
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
    @Operation(operationId = "registerAutoTransfer", summary = "자동이체 등록", description = """
            지정한 출금계좌에서 주기적으로 자동이체를 실행하도록 등록한다. 동일한 Idempotency-Key와 동일한 요청 내용으로 \
            재요청하면 새로 처리하지 않고 저장된 응답을 그대로 반환한다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`AUT0001` 이체지정일 범위 오류 · `AUT0002` 이체기간 오류 · `AUT0004` 종료일 이전 최초 실행 없음 · " +
                            "`AUT0005` 입금 불가 상품유형 · `LMT0002` 1회 이체한도 초과 · `AUT0007` 이체주기 오류 · " +
                            "`AUT0008` 이체금액 오류 · `AUT0009` 통장 표시내용 길이 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`AUT0202` 출금계좌 소유자 아님/비활성 또는 입금계좌를 찾을 수 없음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "`AUT0301` 동일 조건의 자동이체 중복 등록 · `CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<AutoTransferResponse>> register (
            @Parameter(description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
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
    @Operation(operationId = "updateAutoTransfer", summary = "자동이체 변경", description = """
            등록된 자동이체의 금액·이체주기·종료일·통장 표시내용을 변경한다. 출금계좌·입금계좌·이체지정일은 변경할 수 없다. \
            동일한 Idempotency-Key와 동일한 요청 내용으로 재요청하면 새로 처리하지 않고 저장된 응답을 그대로 반환한다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "변경 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`AUT0002` 이체기간 오류 · `AUT0003` 변경 불가 항목 포함 · `AUT0004` 종료일 이전 최초 실행 없음 · " +
                            "`LMT0002` 1회 이체한도 초과 · `AUT0007` 이체주기 오류 · `AUT0008` 이체금액 오류 · `AUT0009` 통장 표시내용 길이 초과",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`AUT0201` 자동이체 등록 건을 찾을 수 없음(본인 소유가 아닌 경우도 동일)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "`AUT0302` 정상 상태가 아닌 자동이체 · `CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<AutoTransferResponse>> change(
            @Parameter(description = "변경할 자동이체 ID", required = true, example = "1")
            @PathVariable Long autoTransferId,
            @Parameter(description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
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
    @Operation(operationId = "cancelAutoTransfer", summary = "자동이체 해지", description = """
            등록된 자동이체를 해지한다. 다음 실행 예정일 당일에는 해지할 수 없다. 동일한 Idempotency-Key와 동일한 \
            요청 내용으로 재요청하면 새로 처리하지 않고 저장된 응답을 그대로 반환한다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "해지 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "`AUT0201` 자동이체 등록 건을 찾을 수 없음(본인 소유가 아닌 경우도 동일)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                    description = "`AUT0302` 정상 상태가 아닌 자동이체 · `AUT0303` 실행 예정일 당일 해지 시도 · `CMN0301`/`CMN0302` 멱등키 충돌",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Void>> cancel(
            @Parameter(description = "해지할 자동이체 ID", required = true, example = "1")
            @PathVariable Long autoTransferId,
            @Parameter(description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환", required = true, example = "550e8400-e29b-41d4-a716-446655440000")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Parameter(description = "계좌 비밀번호 인증 완료 후 발급되는 1회성 인증 토큰", required = true)
            @RequestHeader("Account-Password-Auth-Token") String accountPasswordAuthToken,
            @Parameter(description = "OTP 인증 완료 후 발급되는 1회성 인증 토큰", required = true)
            @RequestHeader("Otp-Auth-Token") String otpAuthToken, HttpServletRequest httpRequest) {

        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        String requestIp = httpRequest.getRemoteAddr();
        String endpoint = "DELETE /auto-transfers/" + autoTransferId;
        AutoTransferCancelCommand command = AutoTransferCancelCommand.builder()
                .customerId(customerId)
                .accountPasswordAuthToken(accountPasswordAuthToken)
                .otpAuthToken(otpAuthToken)
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
    @Operation(operationId = "getAutoTransfers", summary = "자동이체 목록조회", description = "내 출금계좌 기준으로 등록된 자동이체 목록을 상태별로 조회한다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`CMN0001` status 값이 올바르지 않음 · `CMN0002` 필수값 누락 · `CMN0005` 지원하지 않는 페이지 크기(5/10/20/30/50 중 하나여야 함)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<PageResponse<AutoTransferListItemResponse>> search(
            @Parameter(description = "출금계좌 ID (내 계좌)", required = true, example = "1001")
            @RequestParam Long withdrawalAccountId,
            @Parameter(description = "자동이체 상태 필터. 미전달 또는 ALL이면 전체 조회", example = "NORMAL")
            @RequestParam(required = false) String status,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 5/10/20/30/50 중 하나만 허용", example = "10")
            @RequestParam(defaultValue =  "10") int size) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        Page<AutoTransferListItem> result = autoTransferQueryUseCase.search(customerId, withdrawalAccountId, parseStatus(status), page, size);
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
    @Operation(operationId = "getAutoTransferExecutions", summary = "자동이체 처리결과 조회", description = """
            내 출금계좌 기준으로 자동이체 실행 이력을 조회기간별로 조회한다. 조회기간을 지정하지 않으면 \
            최근 1개월(오늘 기준)을 기본값으로 조회한다.""")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "`CMN0002` 필수값 누락 · `CMN0005` 지원하지 않는 페이지 크기(5/10/20/30/50 중 하나여야 함) · " +
                            "`CMN0003` 조회 시작일이 종료일보다 늦음",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<AutoTransferExecutionHistoryPageResponse> searchExecutionHistory(
            @Parameter(description = "출금계좌 ID (내 계좌)", required = true, example = "1001")
            @RequestParam Long withdrawalAccountId,
            @Parameter(description = "조회기간 시작일. 미전달 시 toDate로부터 1개월 전", example = "2026-07-20")
            @RequestParam(required = false) LocalDate fromDate,
            @Parameter(description = "조회기간 종료일. 미전달 시 오늘", example = "2026-08-20")
            @RequestParam(required = false) LocalDate toDate,
            @Parameter(description = "페이지 번호(0부터 시작)", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기. 5/10/20/30/50 중 하나만 허용", example = "10")
            @RequestParam(defaultValue = "10") int size) {
        Long customerId = currentCustomerProvider.getCurrentCustomerId();
        AutoTransferExecutionHistoryResult result = autoTransferExecutionHistoryQueryUseCase.search(
                customerId, withdrawalAccountId, fromDate, toDate, page, size);
        return ApiResponse.success(AutoTransferExecutionHistoryPageResponse.from(result));
    }

}
