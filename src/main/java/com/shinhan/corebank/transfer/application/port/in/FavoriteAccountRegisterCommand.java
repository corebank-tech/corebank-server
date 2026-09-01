package com.shinhan.corebank.transfer.application.port.in;

public record FavoriteAccountRegisterCommand(Long customerId, String depositAccountNumber, String alias) {}
