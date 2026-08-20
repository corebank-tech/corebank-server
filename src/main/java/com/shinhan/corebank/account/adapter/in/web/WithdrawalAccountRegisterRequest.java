package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterCommand;
import jakarta.validation.constraints.NotBlank;

public record WithdrawalAccountRegisterRequest(

        @NotBlank
        String accountPasswordAuthToken,

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