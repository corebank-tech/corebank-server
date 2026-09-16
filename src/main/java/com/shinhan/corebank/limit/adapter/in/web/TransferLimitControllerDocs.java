package com.shinhan.corebank.limit.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.api.AuthenticatedCustomer;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

/**
 * 이체한도 API 의 Swagger 명세. 컨트롤러가 애너테이션에 묻히지 않도록 분리했다.
 * 매핑 애너테이션은 구현체가 갖는다.
 *
 * <p>계좌비밀번호 토큰 무효(APW0102)는 적지 않는다. P6 의 공개 API 가 아직 없어 통과시키므로
 * 발생할 수 없다. OTP 는 otp/api 가 구현돼 있어 실제로 검증되므로 403 을 적는다.
 */
@Tag(name = "이체한도", description = "이체한도 조회·변경 API")
public interface TransferLimitControllerDocs {

    @Operation(
            operationId = "getTransferLimit",
            summary = "이체한도 조회",
            description =
                    """
                    로그인 고객의 1회 이체한도, 1일 이체한도, 당일 사용금액, 당일 잔여 이체가능금액을 조회한다.
                    한도는 계좌가 아니라 고객 단위이므로 계좌 ID를 받지 않는다.
                    당일 사용금액은 KST 영업일 기준이며, 그날 첫 조회라 사용 이력이 없으면 0으로 응답한다.
                    한도를 아직 부여받지 않은 고객에게는 정책 기본값(1회 100만원 · 1일 500만원)으로 응답한다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이체한도 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ApiResponse<TransferLimitResponse> getTransferLimit(@AuthenticationPrincipal AuthenticatedCustomer customer);

    @Operation(
            operationId = "updateTransferLimit",
            summary = "이체한도 변경",
            description =
                    """
                    로그인 고객의 1회·1일 이체한도를 함께 교체한다. 한쪽만 보내는 부분 수정은 허용하지 않는다 —
                    "1회 ≤ 1일" 정합성은 두 값을 함께 봐야 검증할 수 있다.
                    계좌비밀번호 토큰과 OTP 토큰을 모두 요구한다(2단계 인증).
                    OTP 발급 시 transactionData 에 {"oneTimeLimit": 값, "dailyLimit": 값} 을 담아야 하며,
                    이 요청의 한도와 다르면 OTP0102 로 거부된다.
                    응답은 변경된 한도와 당일 사용 현황을 함께 담으므로 재조회할 필요가 없다.
                    동일한 Idempotency-Key와 동일한 요청 내용으로 재요청하면 새로 처리하지 않고 저장된 응답을 반환한다.
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "이체한도 변경 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 필수값 누락·음수·정책 상한(1회 5,000만원 · 1일 1억원) 초과 · " + "`LMT0004` 1회 한도가 1일 한도를 초과함",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`OTP0101` OTP 인증 토큰이 무효·만료·사용됨 · " + "`OTP0102` 인증한 거래 내용과 요청한 한도가 다름",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "`CMN0301`/`CMN0302` 멱등키 충돌",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "`LMT9001` 한도 정보가 없어 변경할 수 없음. " + "가입 연계(REQ-TRSF-029)와 백필이 보장하므로 나오면 데이터 결함이다",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<ApiResponse<TransferLimitResponse>> updateTransferLimit(
            @Parameter(
                            description = "멱등키. 동일 키로 재요청 시 재처리 없이 저장된 응답을 반환",
                            required = true,
                            example = "550e8400-e29b-41d4-a716-446655440000")
                    String idempotencyKey,
            TransferLimitUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedCustomer customer);
}
