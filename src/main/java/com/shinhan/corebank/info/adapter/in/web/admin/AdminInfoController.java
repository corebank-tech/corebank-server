package com.shinhan.corebank.info.adapter.in.web.admin;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.info.application.port.in.DepositBalancesQuery;
import com.shinhan.corebank.info.application.port.in.InfoQueryUseCase;
import com.shinhan.corebank.info.application.port.in.ProductSubscriptionsQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.YearMonth;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 관리자 정보계 조회(PH-77, ADM-03). 인가는 SecurityConfig 의 경로별 GL_READ 매핑이 판정하고 조회 사유는 받지 않는다(POL-A09).
@RestController
@RequestMapping("/admin/info")
@RequiredArgsConstructor
@Tag(name = "관리자 정보계 조회", description = "마트 기반 일별 수신 잔액 추이·상품별 가입/해지 API")
public class AdminInfoController {

    private final InfoQueryUseCase infoQueryUseCase;

    @GetMapping("/deposit-balances")
    @Operation(
            operationId = "getAdminInfoDepositBalances",
            summary = "일별 수신 잔액 추이 조회",
            description =
                    """
                    asOf 이하의 COMPLETED 영업일별 총 수신 잔액을 돌려준다. from·to 를 생략하면 asOf 부터 거슬러 30일이고 최대 범위는 1년이다.
                    적재 이력이 없으면 200 + asOf null + 빈 배열이다.
                    **마트가 준비되기 전에는 적재 이력 없음 응답(asOf null, 빈 목록)을 돌려준다(PH-77 ①).**
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`CMN0102` 권한 없음(GL_READ 필요)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "`INF9001` 정보계 API에 연결하지 못함",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<DepositBalancesResponse> depositBalances(
            @Parameter(description = "조회 시작일(포함). 생략하면 asOf 부터 30일", example = "2026-10-01")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate from,
            @Parameter(description = "조회 종료일(포함). 생략하면 asOf", example = "2026-10-28")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate to) {
        return ApiResponse.success(
                DepositBalancesResponse.from(infoQueryUseCase.depositBalances(new DepositBalancesQuery(from, to))));
    }

    @GetMapping("/product-subscriptions")
    @Operation(
            operationId = "getAdminInfoProductSubscriptions",
            summary = "상품별 가입·해지 조회",
            description =
                    """
                    month(yyyy-MM)에 속한 달의 상품별 가입·해지 건수를 돌려준다. month 를 생략하면 asOf 가 속한 달이다.
                    상품명은 상품별로 가장 최근에 적재된 스냅샷이다. 고객 식별자와 가명 키는 응답에 없다.
                    **마트가 준비되기 전에는 적재 이력 없음 응답(asOf null, 빈 목록)을 돌려준다(PH-77 ①).**
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`CMN0102` 권한 없음(GL_READ 필요)",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "500",
                description = "`INF9001` 정보계 API에 연결하지 못함",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<ProductSubscriptionsResponse> productSubscriptions(
            @Parameter(description = "집계할 달(yyyy-MM). 생략하면 asOf 가 속한 달", example = "2026-10")
                    @RequestParam(required = false)
                    @DateTimeFormat(pattern = "yyyy-MM")
                    YearMonth month) {
        return ApiResponse.success(ProductSubscriptionsResponse.from(
                infoQueryUseCase.productSubscriptions(new ProductSubscriptionsQuery(month))));
    }
}
