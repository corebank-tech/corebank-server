package com.shinhan.corebank.transfer.adapter.in.web;

import com.shinhan.corebank.transfer.application.port.in.FavoriteAccountResult;

public record FavoriteAccountResponse(Long favoriteAccountId, String alias, String depositAccountNumber,
                                       String payeeName, boolean transferable) {
    public static FavoriteAccountResponse from(FavoriteAccountResult result) {
        return new FavoriteAccountResponse(result.favoriteAccountId(), result.alias(),
                result.depositAccountNumber(), result.payeeName(), result.transferable());
    }
}
