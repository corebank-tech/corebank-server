package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountRegisterResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record WithdrawalAccountRegisterResponse(

        @Schema(
                description = "출금계좌로 등록된 계좌의 내부 식별자",
                example = "101"
        )
        Long accountId,

        @Schema(
                description = "출금계좌 등록 시각",
                example = "2026-08-20T15:00:00+09:00"
        )
        OffsetDateTime registeredAt
) {

    public static WithdrawalAccountRegisterResponse from(
            WithdrawalAccountRegisterResult result
    ) {
        return new WithdrawalAccountRegisterResponse(
                result.accountId(),
                result.registeredAt()
        );
    }
}