package com.shinhan.corebank.account.adapter.in.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record AccountAliasChangeRequest(
        @Schema(description = "계좌별명. 전체 최대 24자이며, 한글은 최대 12자", example = "생활비통장") String alias) {}
