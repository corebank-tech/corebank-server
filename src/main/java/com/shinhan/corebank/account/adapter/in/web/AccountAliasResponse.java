package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.AccountAliasResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record AccountAliasResponse(

        @Schema(
                description = "계좌 내부 식별자",
                example = "101"
        )
        Long accountId,

        @Schema(
                description = "설정된 계좌별명",
                example = "생활비통장"
        )
        String alias
) {

    public static AccountAliasResponse from(
            AccountAliasResult result
    ) {
        return new AccountAliasResponse(
                result.accountId(),
                result.alias()
        );
    }
}
