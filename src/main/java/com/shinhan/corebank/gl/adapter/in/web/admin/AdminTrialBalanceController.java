package com.shinhan.corebank.gl.adapter.in.web.admin;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.gl.application.port.in.TrialBalanceQuery;
import com.shinhan.corebank.gl.application.port.in.TrialBalanceQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 관리자 시산표(PH-28, ADM-02). /admin/** 인가는 SecurityConfig 의 임시 허용 목록(#448)이 맡는다 —
// PH-49a 역할 기반 인가가 들어오면 GL_READ 로 바꾼다. 개인정보가 없어 조회 사유(PH-89)는 받지 않는다.
@RestController
@RequestMapping("/admin/trial-balance")
@RequiredArgsConstructor
@Tag(name = "관리자 시산표", description = "기간별·계정별 차변/대변 합계와 차대변 일치 판정 API")
public class AdminTrialBalanceController {

    private final TrialBalanceQueryUseCase trialBalanceQueryUseCase;

    @GetMapping
    @Operation(
            operationId = "getAdminTrialBalance",
            summary = "관리자 시산표 조회",
            description =
                    """
                    거래일이 조회기간(양 끝 포함) 안인 분개를 계정별로 합산한다. 조회기간은 최대 1년(종료일 - 시작일 365일)이다.
                    분류 필터는 받지 않는다 — 총계는 항상 기간 전체 기준이고 행 필터는 화면이 한다(REQ-ADM-023).
                    계정과목에 없는 코드의 분개도 행으로 남기고 계정명·분류·정상잔액을 null 로 내린다(REQ-ADM-024).
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "조회 성공. 분개가 없으면 rows 가 빈 배열이고 balanced=true"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0002` fromDate·toDate 누락 · `CMN0001` 날짜 형식 오류 · "
                        + "`CMN0003` 조회 시작일이 종료일보다 늦음 · `CMN0004` 조회기간이 최대 1년(365일) 초과",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`CMN0102` 관리자 허용 목록 밖",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<TrialBalanceResponse> getTrialBalance(
            @Parameter(description = "조회 시작일(포함)", required = true, example = "2026-09-01")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @Parameter(description = "조회 종료일(포함)", required = true, example = "2026-09-30")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate) {
        return ApiResponse.success(
                TrialBalanceResponse.from(trialBalanceQueryUseCase.query(new TrialBalanceQuery(fromDate, toDate))));
    }
}
