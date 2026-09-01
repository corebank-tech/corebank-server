package com.shinhan.corebank.transfer.application.port.in;

import com.shinhan.corebank.transfer.domain.FavoriteAccount;

public record FavoriteAccountResult(
        Long favoriteAccountId, String alias, String depositAccountNumber, String payeeName, boolean transferable) {

    public static FavoriteAccountResult of(FavoriteAccount favoriteAccount, boolean transferable) {
        return new FavoriteAccountResult(
                favoriteAccount.getFavoriteAccountId(),
                favoriteAccount.getAlias(),
                favoriteAccount.getDepositAccountNumber(),
                favoriteAccount.getPayeeName(),
                transferable);
    }
}
