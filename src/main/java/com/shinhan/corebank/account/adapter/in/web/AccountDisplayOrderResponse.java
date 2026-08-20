package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountDisplayOrderResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record AccountDisplayOrderResponse(

        @Schema(
                description = "저장된 계좌 표시순서",
                example = "[103, 101, 102]"
        )
        List<Long> accountIds
) {

    public AccountDisplayOrderResponse {
        accountIds = List.copyOf(accountIds);
    }

    public static AccountDisplayOrderResponse from(
            AccountDisplayOrderResult result
    ) {
        return new AccountDisplayOrderResponse(
                result.accountIds()
        );
    }
}