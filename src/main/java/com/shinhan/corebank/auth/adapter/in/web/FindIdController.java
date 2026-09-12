package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.adapter.in.web.exception.ErrorResponse;
import com.shinhan.corebank.auth.application.port.in.FindIdResult;
import com.shinhan.corebank.auth.application.port.in.FindIdUseCase;
import com.shinhan.corebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// 고객·계좌 본인확인 후 로그인 아이디를 조회하는 API를 제공한다.
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "인증", description = "로그인과 계정 찾기 API")
public class FindIdController {

    private final FindIdUseCase findIdUseCase;

    @PostMapping("/find-id")
    @Operation(
            operationId = "findId",
            summary = "아이디 찾기",
            description = "성명·생년월일·당행 계좌번호·계좌비밀번호를 검증하고 전체 로그인 아이디를 반환한다.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "아이디 조회 성공"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "`CMN0001` 입력 형식 오류 · `CMN0002` 필수 입력값 누락 · `ATH0009` 고객 또는 계좌정보 불일치",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "`ATH0102` 계좌비밀번호 5회 오류로 계좌 잠금",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "`ATH0201` 일치하는 고객 없음",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ApiResponse<FindIdResponse> findId(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, description = "아이디 찾기 본인확인 정보")
                    @RequestBody(required = false)
                    FindIdRequest request) {
        FindIdResult result = findIdUseCase.findId(request == null ? null : request.toCommand());

        return ApiResponse.success(new FindIdResponse(result.userId()), "아이디를 조회했습니다.");
    }
}
