package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountResult;

import io.swagger.v3.oas.annotations.media.Schema;

public record FavoriteAccountResponse(
        @Schema(description = "즐겨찾기 계좌 ID", example = "301")
        Long favoriteAccountId,
        @Schema(description = "즐겨찾기 별칭", example = "우리 엄마")
        String alias,
        @Schema(description = "입금계좌번호 (하이픈 없이)", example = "11012345678901")
        String depositAccountNumber,
        @Schema(description = "예금주명", example = "홍길동")
        String payeeName,
        @Schema(description = "현재 이체 가능 여부. 등록 이후 계좌가 정지/해지되면 false", example = "true")
        boolean transferable
) {
    public static FavoriteAccountResponse from(FavoriteAccountResult result) {
        return new FavoriteAccountResponse(result.favoriteAccountId(), result.alias(),
                result.depositAccountNumber(), result.payeeName(), result.transferable());
    }
}
