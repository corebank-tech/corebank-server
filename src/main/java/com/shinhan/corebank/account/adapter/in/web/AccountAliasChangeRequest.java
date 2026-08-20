package com.shinhan.corebank.account.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record AccountAliasChangeRequest(

        @Schema(
                description = "계좌별명. 한글 최대 12자, 영문·숫자 최대 24자",
                example = "생활비통장"
        )
        String alias
) {
}
