package com.shinhan.corebank.transfer.adapter.in.web.admin;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;
import com.shinhan.corebank.common.response.PageResponse;
import com.shinhan.corebank.common.util.PageableResolver;
import com.shinhan.corebank.transfer.application.port.in.LedgerReconciliationMismatchQueryUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// 관리자 대사 불일치 목록(PH-38, FE ADM-04). /admin/** 인가는 SecurityConfig의 임시 허용 목록(#448)이 맡는다.
// AUDIT_READ 권한은 PH-49a(#572)가 들어오면 붙인다.
@RestController
@RequestMapping("/admin/ledger-reconciliation")
@RequiredArgsConstructor
@Tag(name = "관리자 원장 대사", description = "원장-잔액 대사 불일치 조회 API")
public class AdminLedgerReconciliationController {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(5, 10, 20, 30, 50);

    private final LedgerReconciliationMismatchQueryUseCase queryUseCase;

    @GetMapping("/mismatches")
    @Operation(
            operationId = "getLedgerReconciliationMismatches",
            summary = "대사 불일치 목록 조회",
            description = "COB 대사가 찾은 원장-잔액 불일치 계좌를 영업일별로 조회한다. 계좌 ID 오름차순이다. "
                    + "businessDate를 빼면 가장 최근에 불일치가 기록된 영업일을 보여준다. 기록이 없으면 items가 빈 배열이다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description =
                        "`CMN0001` 날짜 형식 오류 · `CMN0005` 지원하지 않는 페이지 크기 · " + "`CMN0006` 전체조회(all=true) 결과 100건 초과",
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
    public ApiResponse<PageResponse<LedgerReconciliationMismatchResponse>> getMismatches(
            @Parameter(description = "조회할 영업일(YYYY-MM-DD). 생략 시 가장 최근 영업일", example = "2026-10-13")
                    @RequestParam(required = false)
                    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate businessDate,
            @Parameter(description = "페이지 번호(0부터 시작). all=true면 무시됨", example = "0") @RequestParam(defaultValue = "0")
                    int page,
            @Parameter(description = "페이지 크기. 5/10/20/30/50 중 하나. all=true면 무시됨", example = "10")
                    @RequestParam(defaultValue = "10")
                    int size,
            @Parameter(description = "true면 그 영업일 전체를 반환(최대 100건)", example = "false")
                    @RequestParam(defaultValue = "false")
                    boolean all) {
        var pageable = PageableResolver.resolve(page, size, all, ALLOWED_PAGE_SIZES);
        var result = queryUseCase.find(businessDate, pageable);
        return ApiResponse.success(PageResponse.from(
                result.mismatches(), view -> LedgerReconciliationMismatchResponse.from(result.businessDate(), view)));
    }
}
