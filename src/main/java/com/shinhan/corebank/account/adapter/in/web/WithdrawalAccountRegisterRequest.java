package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record WithdrawalAccountRegisterRequest(

        @Schema(
                description = "출금계좌 등록 용도로 발급된 계좌비밀번호 인증 완료 토큰",
                example = "APW-AUTH-20260730-000001"
        )
        @NotBlank
        String accountPasswordAuthToken,

        @Schema(
                description = "출금계좌 등록 용도로 발급된 OTP 인증 완료 토큰",
                example = "OTP-AUTH-20260730-000001"
        )
        @NotBlank
        String otpAuthToken
) {

    public WithdrawalAccountRegisterCommand toCommand(
            Long customerId,
            Long accountId
    ) {
        return new WithdrawalAccountRegisterCommand(
                customerId,
                accountId,
                accountPasswordAuthToken,
                otpAuthToken
        );
    }
}