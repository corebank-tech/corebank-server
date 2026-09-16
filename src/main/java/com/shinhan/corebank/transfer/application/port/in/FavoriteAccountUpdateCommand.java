package com.shinhan.corebank.transfer.application.port.in;

public record FavoriteAccountUpdateCommand(Long favoriteAccountId, Long customerId, String alias) {}
