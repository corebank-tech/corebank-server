package com.shinhan.corebank.account.adapter.in.web;

import com.shinhan.corebank.account.application.port.in.WithdrawalAccountUnregisterResult;
import io.swagger.v3.oas.annotations.media.Schema;

public record WithdrawalAccountUnregisterResponse(
        @Schema(description = "출금계좌 등록이 삭제된 계좌의 내부 식별자", example = "101") Long accountId,
        @Schema(description = "출금계좌 등록 여부. 삭제 성공 시 false", example = "false") boolean withdrawalAccountRegistered) {

    public static WithdrawalAccountUnregisterResponse from(WithdrawalAccountUnregisterResult result) {
        return new WithdrawalAccountUnregisterResponse(result.accountId(), result.withdrawalAccountRegistered());
    }
}
