package com.shinhan.corebank.signup.adapter.in.web.dto;

import com.shinhan.corebank.signup.application.port.in.VerifySignupAccountCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 회원가입 실명·계좌 인증 HTTP 요청을 표현한다.
public record VerifySignupAccountRequest(
        @Schema(description = "고객 실명", example = "홍길동", maxLength = 50)
                @NotBlank(message = "성명을 입력해 주세요.")
                @Size(max = 50, message = "성명은 50자 이하여야 합니다.")
                String userName,
        @Schema(description = "생년월일 YYMMDD 숫자 6자리", example = "900115", pattern = "^\\d{6}$")
                @NotBlank(message = "생년월일을 입력해 주세요.")
                @Pattern(regexp = "^\\d{6}$", message = "생년월일은 YYMMDD 숫자 6자리여야 합니다.")
                String birthDate,
        @Schema(description = "기존 은행 계좌번호. 하이픈 없는 숫자 12자리", example = "110123456789", pattern = "^\\d{12}$")
                @NotBlank(message = "계좌번호를 입력해 주세요.")
                @Pattern(regexp = "^\\d{12}$", message = "계좌번호는 숫자 12자리여야 합니다.")
                String accountNumber,
        @Schema(
                        description = "기존 계좌비밀번호 숫자 4자리",
                        example = "1234",
                        pattern = "^\\d{4}$",
                        accessMode = Schema.AccessMode.WRITE_ONLY)
                @NotBlank(message = "계좌비밀번호를 입력해 주세요.")
                @Pattern(regexp = "^\\d{4}$", message = "계좌비밀번호는 숫자 4자리여야 합니다.")
                String accountPassword) {

    public VerifySignupAccountCommand toCommand() {
        return new VerifySignupAccountCommand(userName, birthDate, accountNumber, accountPassword);
    }
}
