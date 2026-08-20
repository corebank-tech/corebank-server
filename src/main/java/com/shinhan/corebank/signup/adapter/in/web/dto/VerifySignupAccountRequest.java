package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 회원가입 실명·계좌 인증 HTTP 요청을 표현한다.
public record VerifySignupAccountRequest(
        @NotBlank(message = "성명을 입력해 주세요.")
        @Size(max = 50, message = "성명은 50자 이하여야 합니다.")
        String userName,

        @NotBlank(message = "생년월일을 입력해 주세요.")
        @Pattern(
                regexp = "^\\d{6}$",
                message = "생년월일은 YYMMDD 숫자 6자리여야 합니다."
        )
        String birthDate,

        @NotBlank(message = "계좌번호를 입력해 주세요.")
        @Pattern(
                regexp = "^\\d{12}$",
                message = "계좌번호는 숫자 12자리여야 합니다."
        )
        String accountNumber,

        @NotBlank(message = "계좌비밀번호를 입력해 주세요.")
        @Pattern(
                regexp = "^\\d{4}$",
                message = "계좌비밀번호는 숫자 4자리여야 합니다."
        )
        String accountPassword
) {

    public VerifySignupAccountCommand toCommand() {
        return new VerifySignupAccountCommand(
                userName,
                birthDate,
                accountNumber,
                accountPassword
        );
    }
}
