package com.shinhan.corebank.auth.adapter.in.web;

import com.shinhan.corebank.auth.application.port.in.FindIdCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.NonNull;

// 아이디 찾기 화면의 고객·계좌 본인확인 입력을 받는다.
public record FindIdRequest(
        @Schema(
                description = "고객명",
                example = "홍길동",
                maxLength = 50,
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String customerName,

        @Schema(
                description = "생년월일(YYYY-MM-DD)",
                example = "1999-01-01",
                pattern = "^\\d{4}-\\d{2}-\\d{2}$",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String birthDate,

        @Schema(
                description = "본인 명의 당행 계좌번호(하이픈 없는 숫자 12자리)",
                example = "110550051877",
                pattern = "^\\d{12}$",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String accountNumber,

        @Schema(
                description = "숫자 4자리 계좌비밀번호",
                example = "1234",
                format = "password",
                accessMode = Schema.AccessMode.WRITE_ONLY,
                pattern = "^\\d{4}$",
                requiredMode = Schema.RequiredMode.REQUIRED
        )
        String accountPassword
) {

    public FindIdCommand toCommand() {
        return new FindIdCommand(
                customerName,
                birthDate,
                accountNumber,
                accountPassword
        );
    }

    // 계좌비밀번호가 로그나 디버깅 문자열에 노출되지 않도록 보호한다.
    @Override
    public @NonNull String toString() {
        return "FindIdRequest[customerName=[PROTECTED]"
                + ", birthDate=[PROTECTED]"
                + ", accountNumber=[PROTECTED]"
                + ", accountPassword=[PROTECTED]]";
    }
}
