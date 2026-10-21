package com.shinhan.corebank.gl.adapter.in.web.admin;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.gl.application.port.in.GlIntegrityCheckUseCase;
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

// 관리자 GL 정합성 점검(PH-28b). /admin/** 인가는 SecurityConfig 의 임시 허용 목록(#448)이 맡는다 —
// PH-49a 역할 기반 인가가 들어오면 GL_READ 로 바꾼다. 개인정보가 없어 조회 사유(PH-89)는 받지 않는다.
@RestController
@RequestMapping("/admin/gl/integrity-check")
@RequiredArgsConstructor
@Tag(name = "관리자 GL 정합성 점검", description = "전표 차대변 점검과 원장↔전표 대사 API")
public class AdminGlIntegrityCheckController {

    private final GlIntegrityCheckUseCase glIntegrityCheckUseCase;

    @GetMapping
    @Operation(
            operationId = "getAdminGlIntegrityCheck",
            summary = "관리자 GL 정합성 점검",
            description =
                    """
                    거래일 하루에 대해 두 점검을 돌린다. 결과는 저장하지 않고 요청할 때마다 계산한다.
                    전표 차대변 점검은 차변 합계와 대변 합계가 다르거나 분개가 2줄 미만인 전표를 찾는다.
                    원장↔전표 대사는 원장 거래마다 전표가 있고 차변·대변 합계가 원장 금액과 같은지 본다.
                    대사 대상은 당행 이체(즉시·예약·자동), 상품가입 초입금, 취소정정이고 나머지 유형은 건수만 낸다.
                    원장 없이 전표만 있는 고아 전표는 건수만 낸다. 현재 영업일보다 앞선 거래일만 확정값이다(orphanVoucherCountFinal).
                    """)
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "점검 완료. 결함이 없으면 clean=true 이고 두 목록이 빈 배열"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0002` tradeDate 누락 · `CMN0001` 날짜 형식 오류",
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
    public ApiResponse<GlIntegrityCheckResponse> check(
            @Parameter(description = "점검할 거래일", required = true, example = "2026-10-01")
                    @RequestParam
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate tradeDate) {
        return ApiResponse.success(GlIntegrityCheckResponse.from(glIntegrityCheckUseCase.check(tradeDate)));
    }
}
