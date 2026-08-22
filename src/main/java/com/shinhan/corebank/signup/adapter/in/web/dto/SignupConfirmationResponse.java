package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.SignupConfirmationResult;
import io.swagger.v3.oas.annotations.media.Schema;

// 회원가입 확인 화면의 마스킹된 정보를 HTTP 응답으로 반환한다.
public record SignupConfirmationResponse(
        @Schema(description = "마스킹된 고객명", example = "홍*동")
        String userName,

        @Schema(description = "가입할 로그인 아이디", example = "corebank01")
        String userId,

        @Schema(description = "생년월일", example = "1990-01-15")
        String birthDate,

        @Schema(description = "마스킹된 휴대폰 번호", example = "010****5678")
        String phoneNumber,

        @Schema(description = "마스킹된 이메일", example = "user****@mail.com")
        String email
) {

    public static SignupConfirmationResponse from(
            SignupConfirmationResult result
    ) {
        return new SignupConfirmationResponse(
                result.userName(),
                result.userId(),
                result.birthDate(),
                result.phoneNumber(),
                result.email()
        );
    }
}
