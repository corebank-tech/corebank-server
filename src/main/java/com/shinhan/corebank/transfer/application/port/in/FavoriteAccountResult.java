package com.shinhan.corebank.transfer.application.port.in;

public record FavoriteAccountResult(Long favoriteAccountId, String alias, String depositAccountNumber,
                                     String payeeName, boolean transferable) {
}
