package com.shinhan.corebank.limit.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * 이체한도 API 의 Swagger 명세. 컨트롤러가 애너테이션에 묻히지 않도록 분리했다.
 * 매핑 애너테이션은 구현체가 갖는다.
 */
@Tag(
        name = "이체한도",
        description = "이체한도 조회·변경 API"
)
public interface LimitControllerDocs {

    @Operation(
            summary = "이체한도 조회",
            description = """
                    로그인 고객의 1회 이체한도, 1일 이체한도, 당일 사용금액, 당일 잔여 이체가능금액을 조회한다.
                    한도는 계좌가 아니라 고객 단위이므로 계좌 ID를 받지 않는다.
                    당일 사용금액은 KST 영업일 기준이며, 그날 첫 조회라 사용 이력이 없으면 0으로 응답한다.
                    한도를 아직 부여받지 않은 고객에게는 정책 기본값(1회 100만원 · 1일 500만원)으로 응답한다.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "이체한도 조회 성공"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "`CMN0101` 인증정보가 없거나 세션이 만료됨",
                    content = @Content(
                            schema = @Schema(
                                    implementation = ErrorResponse.class
                            )
                    )
            )
    })
    ApiResponse<LimitResponse> getTransferLimit();
}
